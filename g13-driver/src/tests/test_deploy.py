"""Deployment integration tests; all writes stay in temporary staging roots."""
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1]


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='g13-deploy-test-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.stage = self.base / 'staged'
        self.home = self.base / 'desktop user'
        self.env = dict(os.environ, HOME=str(self.home),
                        XDG_DATA_HOME=str(self.home / 'custom data'),
                        XDG_CONFIG_HOME=str(self.home / 'custom config'))
        self.root = self.stage / (self.home / 'custom data/linux-g13-driver').relative_to('/')
        self.release = self.make_release('g13-test-one')

    def make_release(self, name):
        root = self.base / name
        files = {
            'install.py': (SOURCE / 'scripts/deploy.py').read_bytes(),
            'bin/linux-g13-driver': (SOURCE / 'scripts/launch-driver.sh').read_bytes(),
            'bin/g13-gui': (SOURCE / 'scripts/launch-gui.sh').read_bytes(),
            'bin/g13-release': (SOURCE / 'scripts/launch-release.sh').read_bytes(),
            'libexec/linux-g13-driver': b'#!/bin/sh\nprintf "%s\\n" "$0" "$@"\ncommand -v g13-gui\n',
            'share/java/Linux-G13-GUI.jar': name.encode(),
            'share/udev/99-g13.rules': (SOURCE / 'udev/99-g13.rules').read_bytes(),
            'share/systemd/g13.service.in': (SOURCE / 'systemd/g13.service').read_bytes(),
            'README.md': b'test payload\n',
        }
        host = platform.freedesktop_os_release()
        manifest = {'format': 1, 'release': name, 'version': 'test',
                    'os_id': host.get('ID', 'linux'), 'os_version': host.get('VERSION_ID', ''),
                    'architecture': platform.machine(), 'files': {}}
        for relative, contents in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
            mode = 0o755 if relative.startswith(('bin/', 'libexec/')) or relative == 'install.py' else 0o644
            path.chmod(mode)
            manifest['files'][relative] = {'sha256': hashlib.sha256(contents).hexdigest(), 'mode': mode}
        (root / 'manifest.json').write_text(json.dumps(manifest))
        return root

    def run_deploy(self, command='install', *options, source=None, success=True):
        result = subprocess.run(['python3', str((source or self.release) / 'install.py'), command,
                                 '--destdir', str(self.stage), *options],
                                env=self.env, text=True, capture_output=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result

    def test_update_rollback_and_idempotence_without_source(self):
        self.run_deploy()
        self.run_deploy()  # Reinstalling the same release must not lose rollback history.
        self.assertFalse((self.root / 'previous').exists())
        second = self.make_release('g13-test-two')
        self.run_deploy(source=second)
        self.run_deploy(source=second)
        self.assertEqual(os.readlink(self.root / 'previous'), 'releases/g13-test-one')
        shutil.rmtree(self.release)
        shutil.rmtree(second)
        self.run_deploy('rollback', source=self.root / 'current')
        self.assertEqual(os.readlink(self.root / 'current'), 'releases/g13-test-one')
        self.assertEqual(os.readlink(self.root / 'previous'), 'releases/g13-test-two')
        self.assertIn('g13-test-one', self.run_deploy('status', source=self.root / 'current').stdout)

    def test_corrupt_release_leaves_current_untouched(self):
        self.run_deploy()
        second = self.make_release('g13-test-two')
        (second / 'share/java/Linux-G13-GUI.jar').write_bytes(b'corrupt')
        result = self.run_deploy(source=second, success=False)
        self.assertIn('checksum mismatch', result.stderr)
        self.assertEqual(os.readlink(self.root / 'current'), 'releases/g13-test-one')

    def test_system_layout_and_no_runtime_commands_in_destdir(self):
        self.run_deploy('install', '--scope', 'system')
        root = self.stage / 'usr/local/lib/linux-g13-driver'
        self.assertTrue((root / 'current/manifest.json').exists())
        unit = (self.stage / 'etc/systemd/user/g13.service').read_text()
        self.assertIn('ExecStart="/usr/local/lib/linux-g13-driver/current/bin/linux-g13-driver"', unit)
        self.assertNotIn(str(self.stage), unit)
        self.assertTrue((self.stage / 'etc/udev/rules.d/99-g13.rules').is_file())
        self.assertEqual(os.readlink(self.stage / 'usr/local/bin/g13-gui'),
                         '/usr/local/lib/linux-g13-driver/current/bin/g13-gui')

    def test_user_layout_preserves_configuration_and_launchers_relocate(self):
        config = self.stage / (self.home / 'custom config/g13/bindings-0.properties').relative_to('/')
        config.parent.mkdir(parents=True)
        config.write_text('G0=p,k.17\n')
        self.run_deploy('install', '--activate')  # DESTDIR must suppress activation.
        self.assertEqual(config.read_text(), 'G0=p,k.17\n')
        self.assertFalse((self.stage / 'etc/udev').exists())
        unit = self.stage / (self.home / 'custom config/systemd/user/g13.service').relative_to('/')
        self.assertIn(str(self.home / 'custom data/linux-g13-driver/current/bin'), unit.read_text())
        result = subprocess.run([str(self.root / 'current/bin/linux-g13-driver'), 'two words'],
                                text=True, capture_output=True, check=True)
        self.assertIn('two words', result.stdout)
        self.assertIn(str(self.root / 'releases/g13-test-one/bin/g13-gui'), result.stdout)
        fakebin = self.base / 'fake-bin'
        fakebin.mkdir()
        java = fakebin / 'java'
        java.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
        java.chmod(0o755)
        result = subprocess.run([str(self.root / 'current/bin/g13-gui'), 'two words'],
                                env=dict(self.env, PATH=str(fakebin) + ':' + os.environ['PATH']),
                                text=True, capture_output=True, check=True)
        self.assertEqual(result.stdout.splitlines(), ['-jar',
                         str(self.root / 'releases/g13-test-one/share/java/Linux-G13-GUI.jar'), 'two words'])

    def test_legacy_requires_explicit_migration_and_keeps_backup(self):
        binary = self.stage / (self.home / '.local/bin/linux-g13-driver').relative_to('/')
        binary.parent.mkdir(parents=True)
        binary.write_text('old driver')
        self.run_deploy(success=False)
        self.assertEqual(binary.read_text(), 'old driver')
        self.run_deploy('install', '--replace-legacy')
        self.assertTrue(binary.is_symlink())
        self.assertEqual(binary.with_name(binary.name + '.g13-before-release').read_text(), 'old driver')

    def test_wrong_platform_and_unexpected_payload_rejected(self):
        manifest_path = self.release / 'manifest.json'
        manifest = json.loads(manifest_path.read_text())
        manifest['architecture'] = 'wrong-architecture'
        manifest_path.write_text(json.dumps(manifest))
        self.run_deploy(success=False)
        self.assertFalse((self.root / 'current').exists())
        self.run_deploy('install', '--allow-platform-mismatch')
        (self.release / 'unexpected').write_text('not in manifest')
        result = self.run_deploy('install', '--allow-platform-mismatch', success=False)
        self.assertIn('extra files', result.stderr)

    def test_rollback_without_history_and_symlink_payload_rejected(self):
        self.run_deploy('rollback', success=False)
        binary = self.release / 'libexec/linux-g13-driver'
        binary.unlink()
        binary.symlink_to('/bin/true')
        self.assertIn('symlink', self.run_deploy(success=False).stderr)

    def test_device_setup_can_be_staged_separately(self):
        self.run_deploy('hardware')
        self.assertTrue((self.stage / 'etc/udev/rules.d/99-g13.rules').is_file())
        self.assertFalse((self.root / 'current').exists())


if __name__ == '__main__':
    unittest.main()
