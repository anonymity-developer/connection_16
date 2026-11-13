import base64
import importlib.util
import io
import sys
import tarfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    sys.modules[name] = result
    spec.loader.exec_module(result)
    return result


env = module('validate_env', ROOT / 'infra/ec2/validate-env.py')
release = module('package_release', ROOT / 'scripts/deploy/package-release.py')
controller = module('poll_release', ROOT / 'infra/ec2/poll-release.py')


class DeploymentSafetyTest(unittest.TestCase):
    def settings(self, **changes):
        values = {
            'DB_PASSWORD': 'local-test-database-password',
            'DB_ROOT_PASSWORD': 'local-test-separate-root-password',
            'ADMIN_BOOTSTRAP_ID': 'admin:test',
            'ADMIN_BOOTSTRAP_PASSWORD': 'local-test-admin-password',
            'AUTH_HMAC_KEY': base64.b64encode(b'test-only-key-material-at-least-32-bytes').decode(),
            'AUTH_ENABLED': 'false',
            'APP_DOMAIN': '',
        }
        values.update(changes)
        return '\n'.join(f'{key}={value}' for key, value in values.items())

    def test_domain_is_optional_for_private_rehearsal(self):
        self.assertEqual(env.validate(self.settings()), '')

    def test_public_domain_cannot_turn_into_a_caddy_directive(self):
        for domain in ('http://example.net', 'example.net/path', 'tagme.example.com',
                       '127.0.0.1', 'example.net:80', 'example.net { respond bad }'):
            with self.subTest(domain=domain), self.assertRaises(ValueError):
                env.validate(self.settings(APP_DOMAIN=domain))
        self.assertEqual(env.validate(self.settings(APP_DOMAIN='tagme.event.test')), 'tagme.event.test')

    def test_placeholders_and_short_hmac_are_rejected_without_exposing_secrets(self):
        for key, value in (('DB_PASSWORD', 'change-me-placeholder'),
                           ('AUTH_HMAC_KEY', base64.b64encode(b'short-key-material').decode())):
            with self.subTest(key=key), self.assertRaises(ValueError) as caught:
                env.validate(self.settings(**{key: value}))
            self.assertNotIn(value, str(caught.exception))

    def test_dotenv_is_never_executed_and_external_credentials_stay_literal(self):
        with self.assertRaises(ValueError):
            env.validate(self.settings(DB_PASSWORD='$(touch /tmp/unsafe)'))
        self.assertEqual(env.validate(self.settings(DB_PASSWORD="'literal-$credential#with space'")), '')
        with self.assertRaises(ValueError) as caught:
            env.validate(self.settings(DB_PASSWORD="'secret'with'unescaped-quotes'"))
        self.assertNotIn('unescaped-quotes', str(caught.exception))

    def test_compose_target_and_aws_overrides_are_rejected(self):
        for key in ('COMPOSE_PROJECT_NAME', 'COMPOSE_FILE', 'AWS_ENDPOINT_URL', 'TAGME_REGISTRY'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                env.validate(self.settings(**{key: 'unexpected'}))

    def test_enabled_auth_requires_one_complete_sender_and_channel(self):
        with self.assertRaises(ValueError):
            env.validate(self.settings(AUTH_ENABLED='true'))
        settings = dict(AUTH_ENABLED='true', MATTERMOST_TOKEN='test-token',
                        MATTERMOST_CHANNEL_ID='test-channel', MATTERMOST_BASE_URL='https://meeting.ssafy.com')
        self.assertEqual(env.validate(self.settings(**settings)), '')
        with self.assertRaises(ValueError):
            env.validate(self.settings(**settings, MATTERMOST_LOGIN_ID='sender'))

    def test_release_bundle_excludes_all_runtime_secrets(self):
        with tarfile.open(fileobj=io.BytesIO(release.bundle(ROOT)), mode='r:gz') as archive:
            self.assertEqual(set(archive.getnames()), set(release.FILES))
            self.assertFalse(any(name in archive.getnames() for name in ('.env', '.env.pr-review', '.env.production', '.env.fixtures', 'compose.fixtures.yml')))
            for member in archive.getmembers():
                self.assertTrue(member.isfile())
                self.assertNotIn(b'\r\n', archive.extractfile(member).read())

    def metadata(self):
        sha = 'a' * 40
        tag = sha + '-1-1'
        manifest = dict(schema=1, commit_sha=sha, image_tag=tag, workflow_run_id=1,
                        workflow_run_attempt=1, runtime_sha256='b' * 64, images_sha256='c' * 64)
        published = dict(tag_name='tagme-deploy-' + tag, target_commitish=sha,
                         author={'login': 'github-actions[bot]'}, draft=False, prerelease=False)
        return manifest, published

    def test_manual_or_mismatched_release_cannot_be_deployed(self):
        manifest, published = self.metadata()
        self.assertEqual(controller.validate_manifest(manifest, published), manifest['image_tag'])
        for change in ({'author': {'login': 'someone'}}, {'target_commitish': 'main'}, {'draft': True}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                controller.validate_manifest(manifest, dict(published, **change))
        for tag in ('../current;id', 'a' * 40 + '-2-1'):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                controller.validate_manifest(dict(manifest, image_tag=tag), published)

    def test_foreign_downloads_are_rejected(self):
        _, published = self.metadata()
        published['assets'] = [dict(name='images.tar.gz', size=100,
                                    browser_download_url='https://attacker.invalid/images.tar.gz',
                                    uploader={'login': 'github-actions[bot]'})]
        with self.assertRaises(ValueError):
            controller.asset(published, 'images.tar.gz', 1000)

    def test_tar_symlinks_and_traversal_are_rejected_before_extraction(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'bad.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                entry = tarfile.TarInfo('../outside')
                archive.addfile(entry, io.BytesIO(b''))
            with self.assertRaises(ValueError):
                controller.extract_runtime(path, Path(temp) / 'runtime')
            self.assertFalse((Path(temp) / 'outside').exists())

    def test_image_archive_cannot_replace_unrelated_images(self):
        import tempfile, json
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'images.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                data = json.dumps([{'RepoTags': ['mysql:8.4']}]).encode()
                entry = tarfile.TarInfo('manifest.json'); entry.size = len(data)
                archive.addfile(entry, io.BytesIO(data))
            with self.assertRaises(ValueError):
                controller.verify_images(path, 'a' * 40 + '-1-1')


if __name__ == '__main__':
    unittest.main()
