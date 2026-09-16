#!/usr/bin/env python3
"""Install and switch verified G13 releases without a source checkout."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile

COMMANDS = ('linux-g13-driver', 'g13-gui', 'g13-release')
MARKER = '# Managed by the G13 release installer.'
REQUIRED = {'install.py', 'libexec/linux-g13-driver', 'share/java/Linux-G13-GUI.jar',
            'share/systemd/g13.service.in', 'share/udev/99-g13.rules', 'README.md'} | {
                'bin/' + command for command in COMMANDS}


def verify(payload):
    manifest = json.loads((payload / 'manifest.json').read_text())
    if manifest.get('format') != 1 or not re.fullmatch(r'g13-[A-Za-z0-9._-]+', manifest.get('release', '')):
        raise ValueError('Unsupported release manifest')
    if set(manifest['files']) != REQUIRED:
        raise ValueError('Release has an unexpected file inventory')
    actual = set()
    for path in payload.rglob('*'):
        if path.is_symlink():
            raise ValueError(f'Release contains a symlink: {path}')
        if path.is_file():
            actual.add(str(path.relative_to(payload)))
    if actual != REQUIRED | {'manifest.json'}:
        raise ValueError('Release contains missing or extra files')
    for relative, expected in manifest['files'].items():
        path = payload / relative
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected['sha256']:
            raise ValueError(f'Release checksum mismatch: {relative}')
        mode = 0o755 if relative.startswith(('bin/', 'libexec/')) or relative == 'install.py' else 0o644
        if expected['mode'] != mode or path.stat().st_mode & 0o777 != mode:
            raise ValueError(f'Release permission mismatch: {relative}')
    return manifest


def platform_check(manifest, allow):
    host = platform.freedesktop_os_release()
    matches = (manifest['architecture'] == platform.machine() and
               manifest['os_id'] == host.get('ID', 'linux') and
               manifest['os_version'] == host.get('VERSION_ID', ''))
    if not matches and not allow:
        raise ValueError('Release targets a different distro/version or architecture. Build on this system '
                         'or use a matching archive; --allow-platform-mismatch overrides this check.')


def runtime_check(payload):
    if not shutil.which('java'):
        raise ValueError('Java 17 or newer is required. Install your distribution\'s Java runtime.')
    java = subprocess.run(['java', '-version'], text=True, capture_output=True, check=True)
    match = re.search(r'version "(\d+)', java.stderr + java.stdout)
    if not match or int(match[1]) < 17:
        raise ValueError('Java 17 or newer is required.')
    result = subprocess.run(['ldd', str(payload / 'libexec/linux-g13-driver')],
                            text=True, capture_output=True)
    if result.returncode or 'not found' in result.stdout:
        raise ValueError('Native runtime dependencies are unavailable:\n' + result.stdout + result.stderr)


def absolute(value):
    path = Path(value)
    if not path.is_absolute() or any(c in str(path) for c in '\n\r\x00:'):
        raise ValueError(f'Expected an absolute path without newline or colon: {value}')
    if '..' in path.parts:
        raise ValueError(f'Parent traversal is not supported: {value}')
    return path


class Layout:
    def __init__(self, args):
        self.destdir = absolute(args.destdir) if args.destdir else None
        if self.destdir == Path('/'):
            raise ValueError('DESTDIR must be a separate staging directory, not /')
        if args.scope == 'system':
            self.root = Path('/usr/local/lib/linux-g13-driver')
            self.bin = Path('/usr/local/bin')
            self.unit = Path('/etc/systemd/user/g13.service')
        else:
            home = Path.home()
            self.root = absolute(os.environ.get('XDG_DATA_HOME') or str(home / '.local/share')) / 'linux-g13-driver'
            self.bin = home / '.local/bin'
            self.unit = absolute(os.environ.get('XDG_CONFIG_HOME') or str(home / '.config')) / 'systemd/user/g13.service'
        self.rules = Path('/etc/udev/rules.d/99-g13.rules')
        for path in (self.root, self.bin, self.unit):
            absolute(str(path))

    def disk(self, path):
        return self.destdir / path.relative_to('/') if self.destdir else path


def exists(path):
    return path.exists() or path.is_symlink()


def atomic_write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix='.g13-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(content)
        os.chmod(temp, 0o644)
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def atomic_link(path, target):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.g13-link-', dir=path.parent) as temp:
        link = Path(temp) / 'link'
        link.symlink_to(target)
        os.replace(link, path)


def selected(root, name):
    link = root / name
    if not exists(link):
        return None
    if not link.is_symlink():
        raise ValueError(f'Refusing unmanaged release pointer: {link}')
    target = Path(os.readlink(link))
    if len(target.parts) != 2 or target.parts[0] != 'releases' or not re.fullmatch(r'g13-[A-Za-z0-9._-]+', target.name):
        raise ValueError(f'Invalid release pointer: {link}')
    manifest = verify(root / target)
    if manifest['release'] != target.name:
        raise ValueError('Installed release name does not match its manifest')
    return target


def unit_text(payload, layout):
    # systemd quoting, specifier expansion, and ExecStart dollar expansion.
    value = str(layout.root / 'current/bin/linux-g13-driver')
    value = value.replace('\\', '\\\\').replace('"', '\\"').replace('%', '%%').replace('$', '$$')
    return (payload / 'share/systemd/g13.service.in').read_text().replace('@DRIVER@', '"' + value + '"')


def integration_conflicts(layout, scope, payload):
    conflicts = []
    for command in COMMANDS:
        path = layout.disk(layout.bin / command)
        target = str(layout.root / 'current/bin' / command)
        if exists(path) and not (path.is_symlink() and os.readlink(path) == target):
            conflicts.append(path)
    unit = layout.disk(layout.unit)
    if exists(unit) and (unit.is_symlink() or not unit.is_file() or not unit.read_text().startswith(MARKER)):
        conflicts.append(unit)
    if scope == 'system':
        rule = layout.disk(layout.rules)
        if exists(rule) and (rule.is_symlink() or not rule.is_file() or
                             rule.read_bytes() != (payload / 'share/udev/99-g13.rules').read_bytes()):
            conflicts.append(rule)
    return conflicts


def register(layout, scope, payload):
    for command in COMMANDS:
        atomic_link(layout.disk(layout.bin / command), str(layout.root / 'current/bin' / command))
    atomic_write(layout.disk(layout.unit), unit_text(payload, layout))
    if scope == 'system':
        atomic_write(layout.disk(layout.rules), (payload / 'share/udev/99-g13.rules').read_text())


@contextlib.contextmanager
def locked(root):
    root.mkdir(parents=True, exist_ok=True)
    with (root / '.deploy.lock').open('a') as stream:
        fcntl.flock(stream, fcntl.LOCK_EX)
        yield


def deploy(args, layout, source):
    root = layout.disk(layout.root)
    # Check everything possible before touching the installed selection.
    manifest = verify(source) if args.command == 'install' else None
    with locked(root):
        current = selected(root, 'current')
        previous = selected(root, 'previous')
        if args.command == 'rollback':
            if not previous:
                raise ValueError('No previous release is available')
            source = root / previous
            manifest = verify(source)
        platform_check(manifest, args.allow_platform_mismatch)
        if not layout.destdir:
            runtime_check(source)
        conflicts = integration_conflicts(layout, args.scope, source)
        if conflicts and not args.replace_legacy:
            raise ValueError('Existing unmanaged installation files: ' + ', '.join(map(str, conflicts)) +
                             '. Use --replace-legacy to back them up and migrate.')
        for path in conflicts:
            if path.is_dir() or exists(path.with_name(path.name + '.g13-before-release')):
                raise ValueError(f'Cannot safely back up {path}; resolve the directory or existing backup first')
        relative = Path('releases') / manifest['release']
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if verify(target) != manifest:
                raise ValueError('An installed release with this ID has different contents')
        else:
            with tempfile.TemporaryDirectory(prefix='.incoming-', dir=target.parent) as temporary:
                copy = Path(temporary) / 'payload'
                shutil.copytree(source, copy)
                verify(copy)
                copy.rename(target)
        for path in conflicts:
            path.rename(path.with_name(path.name + '.g13-before-release'))
        register(layout, args.scope, target)
        if current and current != relative:
            atomic_link(root / 'previous', str(current))
        atomic_link(root / 'current', str(relative))
    print(f'Installed release: {manifest["release"]}\nLocation: {layout.root / "current"}')
    if layout.destdir:
        print(f'Staged under {layout.destdir}; no services or device rules were reloaded.')
        return
    if args.scope == 'system':
        print('Run as each desktop user: systemctl --user daemon-reload && '
              'systemctl --user enable g13.service && systemctl --user restart g13.service')
        print('A user-local g13.service overrides this system-wide unit; migrate or remove that override first.')
        print('Reload device rules with sudo udevadm control --reload-rules, then reconnect the G13 if needed.')
    else:
        print('Device permissions are a separate, one-time setup: sudo python3 ' +
              str(target / 'install.py') + ' hardware')
        if args.activate:
            for operation in ('daemon-reload', 'enable', 'restart'):
                command = ['systemctl', '--user', operation]
                if operation != 'daemon-reload':
                    command.append('g13.service')
                subprocess.run(command, check=True)
        else:
            print('Activate with: systemctl --user daemon-reload && systemctl --user enable g13.service && '
                  'systemctl --user restart g13.service')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('install', 'rollback', 'status', 'hardware'))
    parser.add_argument('--scope', choices=('user', 'system'), default='user')
    parser.add_argument('--destdir', help='Stage files beneath this directory; never run service/device commands')
    parser.add_argument('--activate', action='store_true', help='Enable/restart the invoking user\'s service after deployment')
    parser.add_argument('--replace-legacy', action='store_true', help='Back up conflicting files with .g13-before-release suffix')
    parser.add_argument('--allow-platform-mismatch', action='store_true')
    args = parser.parse_args()
    if args.activate and (args.scope != 'user' or args.command not in ('install', 'rollback')):
        parser.error('--activate is only supported for user installation or rollback')
    layout = Layout(args)
    if not layout.destdir and args.command != 'status':
        if (args.scope == 'system' or args.command == 'hardware') and os.geteuid() != 0:
            raise ValueError('System installation/device setup requires root; rerun with sudo')
        if args.scope == 'user' and args.command != 'hardware' and os.geteuid() == 0:
            raise ValueError('Run user installation as your desktop user, without sudo')
    source = Path(__file__).resolve().parent
    if args.command == 'status':
        root = layout.disk(layout.root)
        for name in ('current', 'previous'):
            print(f'{name}: {selected(root, name) or "none"}')
    elif args.command == 'hardware':
        verify(source)
        rule = layout.disk(layout.rules)
        content = (source / 'share/udev/99-g13.rules').read_text()
        if exists(rule) and (rule.is_symlink() or rule.read_text() != content):
            raise ValueError(f'Existing custom rule must be reviewed before replacing: {rule}')
        atomic_write(rule, content)
        if not layout.destdir:
            subprocess.run(['udevadm', 'control', '--reload-rules'], check=True)
        print('Device rule installed. Reconnect the G13 if permissions need to be refreshed.')
    else:
        deploy(args, layout, source)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f'G13 deployment failed: {error}', file=sys.stderr)
        sys.exit(1)
