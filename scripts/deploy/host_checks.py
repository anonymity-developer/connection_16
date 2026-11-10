"""Exercise deployment failure boundaries in an isolated container with fake AWS/Docker."""
import base64
import os
import shutil
import subprocess
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[2]
ROOT = Path('/opt/tagme')
TAG = 'a' * 40 + '-1-1'


@unittest.skipUnless(Path('/.dockerenv').exists(), 'Run only inside the disposable test container')
class HostDeploymentTest(unittest.TestCase):
    def setUp(self):
        if ROOT.exists():
            shutil.rmtree(ROOT)
        self.release = ROOT / 'releases' / TAG
        (self.release / 'infra/ec2').mkdir(parents=True)
        for name in ('deploy.sh', 'validate-env.py'):
            shutil.copyfile(SOURCE / 'infra/ec2' / name, self.release / 'infra/ec2' / name)
        shutil.copyfile(SOURCE / '.env.production.example', self.release / '.env.production.example')
        previous = ROOT / 'releases/previous'
        previous.mkdir()
        (ROOT / 'current').symlink_to(previous)
        (ROOT / '.env.production').write_text('previous-settings')
        self.log = ROOT / 'commands.log'
        self.secret = 'private-test-credential-DO-NOT-PRINT'
        self.parameter = ROOT / 'parameter.env'
        self.parameter.write_text(
            f'DB_PASSWORD={self.secret}\nDB_ROOT_PASSWORD=separate-test-root-password\n'
            'ADMIN_BOOTSTRAP_ID=admin:test\nADMIN_BOOTSTRAP_PASSWORD=separate-test-admin-password\n'
            f'AUTH_HMAC_KEY={base64.b64encode(bytes(range(32))).decode()}\n'
            'AUTH_ENABLED=false\nAPP_DOMAIN=\n'
        )
        fake = ROOT / 'bin'
        fake.mkdir()
        (fake / 'aws').write_text('#!/bin/bash\nset -eu\nif [[ "$1" == ssm ]]; then cat "$CHECK_PARAMETER"; else echo fake-ecr-password; fi\n')
        (fake / 'docker').write_text('''#!/bin/bash
set -eu
echo "$*" >> "$CHECK_LOG"
if [[ "$1" == login ]]; then cat >/dev/null; exit 0; fi
if [[ "$1" == volume ]]; then [[ "$CHECK_MODE" == orphan ]]; exit; fi
if [[ "$1" == ps ]]; then
  if [[ "$CHECK_MODE" != fresh && "$CHECK_MODE" != orphan ]]; then
    if [[ "$CHECK_MODE" != stopped || "$2" == -aq ]]; then echo test-mysql; fi
  fi
  exit 0
fi
if [[ "$1" == exec ]]; then
  [[ "$CHECK_MODE" != backup-failure ]] || exit 31
  echo 'CREATE TABLE test_backup (id INT);'
  exit 0
fi
case "$*" in
  *" pull mysql redis") [[ "$CHECK_MODE" != pull-failure ]] || exit 32 ;;
  *" up "*) [[ "$CHECK_MODE" != health-failure ]] || exit 33 ;;
esac
''')
        for executable in fake.iterdir():
            executable.chmod(0o700)
        self.environment = dict(os.environ, PATH=str(fake) + ':' + os.environ['PATH'],
                                CHECK_PARAMETER=str(self.parameter), CHECK_LOG=str(self.log))

    def run_deploy(self, mode):
        self.environment['CHECK_MODE'] = mode
        result = subprocess.run(['bash', str(self.release / 'infra/ec2/deploy.sh'), TAG],
                                env=self.environment, capture_output=True, text=True)
        self.assertNotIn(self.secret, result.stdout + result.stderr)
        return result

    def assert_unchanged(self):
        self.assertEqual((ROOT / 'current').resolve().name, 'previous')
        self.assertEqual((ROOT / '.env.production').read_text(), 'previous-settings')

    def test_fresh_private_success_promotes_release_without_publishing(self):
        result = self.run_deploy('fresh')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((ROOT / 'current').resolve(), self.release)
        self.assertEqual((ROOT / '.env.production').stat().st_mode & 0o077, 0)
        self.assertNotIn('compose.prod.yml', self.log.read_text())

    def test_existing_database_success_takes_backup_before_up(self):
        result = self.run_deploy('existing')
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.log.read_text()
        self.assertLess(calls.index('mysqldump'), calls.index(' up '))
        self.assertEqual(len(list((ROOT / 'backups').glob('*.sql.gz'))), 1)

    def test_pull_and_backup_failures_never_start_a_new_release(self):
        for mode in ('pull-failure', 'backup-failure'):
            with self.subTest(mode=mode):
                self.assertNotEqual(self.run_deploy(mode).returncode, 0)
                self.assert_unchanged()
                self.assertNotIn(' up ', self.log.read_text())

    def test_failed_readiness_does_not_promote_or_erase_backup(self):
        self.assertNotEqual(self.run_deploy('health-failure').returncode, 0)
        self.assert_unchanged()
        self.assertEqual(len(list((ROOT / 'backups').glob('*.sql.gz'))), 1)
        self.assertNotIn('down', self.log.read_text())

    def test_stopped_and_orphaned_databases_require_recovery_first(self):
        for mode in ('stopped', 'orphan'):
            with self.subTest(mode=mode):
                self.assertNotEqual(self.run_deploy(mode).returncode, 0)
                self.assert_unchanged()
                self.assertNotIn(' up ', self.log.read_text())

    def test_bad_environment_stops_before_any_docker_request(self):
        self.parameter.write_text('DB_PASSWORD=change-me\n')
        self.assertNotEqual(self.run_deploy('fresh').returncode, 0)
        self.assert_unchanged()
        self.assertFalse(self.log.exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
