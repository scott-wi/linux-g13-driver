#!/usr/bin/env python3
"""Build a relocatable, host-specific release from compiled artifacts."""
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'g13-driver/src'


def main():
    output = ROOT / 'dist'
    output.mkdir(exist_ok=True)
    version = ET.parse(ROOT / 'g13-config-tool/pom.xml').findtext(
        '{http://maven.apache.org/POM/4.0.0}version')
    revision = subprocess.check_output(
        ['git', '-C', str(ROOT), 'rev-parse', '--short', 'HEAD'], text=True).strip()
    dirty = bool(subprocess.check_output(
        ['git', '-C', str(ROOT), 'status', '--porcelain', '--untracked-files=normal']))
    host = platform.freedesktop_os_release()
    with tempfile.TemporaryDirectory(prefix='.release-', dir=output) as temporary:
        payload = Path(temporary) / 'payload'
        files = {
            ROOT / 'g13-driver/build/Linux-G13-Driver': 'libexec/linux-g13-driver',
            ROOT / 'g13-config-tool/target/Linux-G13-GUI.jar': 'share/java/Linux-G13-GUI.jar',
            SOURCE / 'scripts/deploy.py': 'install.py',
            SOURCE / 'scripts/launch-driver.sh': 'bin/linux-g13-driver',
            SOURCE / 'scripts/launch-gui.sh': 'bin/g13-gui',
            SOURCE / 'scripts/launch-release.sh': 'bin/g13-release',
            SOURCE / 'systemd/g13.service': 'share/systemd/g13.service.in',
            SOURCE / 'udev/99-g13.rules': 'share/udev/99-g13.rules',
            ROOT / 'docs/releases.md': 'README.md',
        }
        for src, relative in files.items():
            dest = payload / relative
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dest)
            dest.chmod(0o755 if relative.startswith(('bin/', 'libexec/')) or relative == 'install.py' else 0o644)
        manifest = {
            'format': 1, 'version': version, 'revision': revision, 'dirty': dirty,
            'os_id': host.get('ID', 'linux'), 'os_version': host.get('VERSION_ID', ''),
            'architecture': platform.machine(),
            'files': {str(p.relative_to(payload)): {
                'sha256': hashlib.sha256(p.read_bytes()).hexdigest(),
                'mode': p.stat().st_mode & 0o777,
            } for p in sorted(payload.rglob('*')) if p.is_file()},
        }
        digest = hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()[:16]
        label = '-'.join([version, manifest['os_id'], manifest['os_version'], manifest['architecture'], digest])
        manifest['release'] = 'g13-' + re.sub(r'[^A-Za-z0-9._-]', '-', label)
        (payload / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        destination = output / manifest['release']
        if destination.exists():
            # Immutable release names are content-derived; never overwrite one.
            for p in payload.rglob('*'):
                if p.is_file() and p.read_bytes() != (destination / p.relative_to(payload)).read_bytes():
                    raise RuntimeError(f'Existing release differs: {destination}')
        else:
            payload.rename(destination)
        archive = output / (manifest['release'] + '.tar.gz')
        temporary_archive = Path(temporary) / 'release.tar.gz'
        with tarfile.open(temporary_archive, 'w:gz') as tar:
            tar.add(destination, arcname=destination.name)
        os.replace(temporary_archive, archive)
        link = Path(temporary) / 'latest'
        link.symlink_to(destination.name)
        os.replace(link, output / 'latest')
        print(f'Release: {destination}\nArchive: {archive}')


if __name__ == '__main__':
    main()
