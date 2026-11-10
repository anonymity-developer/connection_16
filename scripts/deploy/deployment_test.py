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
release = module('send_release', ROOT / 'scripts/deploy/send-release.py')


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
        tag = 'a' * 40 + '-1-1'
        result = release.parameters(ROOT, '123456789012.dkr.ecr.ap-southeast-2.amazonaws.com', tag)
        command = result['commands'][0]
        encoded = command.split("printf '%s' '", 1)[1].split("'", 1)[0]
        with tarfile.open(fileobj=io.BytesIO(base64.b64decode(encoded)), mode='r:gz') as archive:
            self.assertEqual(set(archive.getnames()), set(release.FILES))
            self.assertFalse(any(name in archive.getnames() for name in ('.env', '.env.pr-review', '.env.production')))
            for member in archive.getmembers():
                self.assertTrue(member.isfile())
                self.assertNotIn(b'\r\n', archive.extractfile(member).read())

    def test_shell_injection_in_registry_or_release_tag_never_reaches_ssm(self):
        for registry, tag in (
            ('123456789012.dkr.ecr.ap-southeast-2.amazonaws.com;id', 'a' * 40 + '-1-1'),
            ('123456789012.dkr.ecr.ap-southeast-2.amazonaws.com', '../current;id'),
            ('123456789012.dkr.ecr.us-east-1.amazonaws.com', 'a' * 40 + '-1-1'),
        ):
            with self.subTest(registry=registry, tag=tag), self.assertRaises(ValueError):
                release.parameters(ROOT, registry, tag)


if __name__ == '__main__':
    unittest.main()
