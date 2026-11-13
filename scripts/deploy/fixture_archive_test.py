import importlib.util
import io
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('verify_api_archive', Path(__file__).with_name('verify-api-archive.py'))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class FixtureArchiveTest(unittest.TestCase):
    def archive(self, *extra):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('BOOT-INF/classes/com/tagme/TagmeApplication.class', b'application')
            for name in extra:
                archive.writestr(name, b'fixture')
        buffer.seek(0)
        return buffer

    def test_accepts_normal_application(self):
        verifier.verify(self.archive('BOOT-INF/classes/com/tagme/auth/LocalAdminService.class'))

    def test_rejects_seeder_and_future_fixture_classes(self):
        for name in ('DevFixtureSeeder.class', 'DevFixtureSafety.class', 'FutureFixture.class'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                verifier.verify(self.archive('BOOT-INF/classes/com/tagme/devfixture/' + name))

    def test_rejects_auth_adapter_and_nested_classes(self):
        for name in ('DevFixtureMattermostClient.class', 'DevFixtureMattermostClient$Identity.class'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                verifier.verify(self.archive('BOOT-INF/classes/com/tagme/auth/' + name))

    def test_rejects_unrelated_or_damaged_archives(self):
        empty = io.BytesIO()
        with zipfile.ZipFile(empty, 'w'):
            pass
        empty.seek(0)
        with self.assertRaises(ValueError):
            verifier.verify(empty)
        with self.assertRaises(zipfile.BadZipFile):
            verifier.verify(io.BytesIO(b'not a jar'))


if __name__ == '__main__':
    unittest.main()
