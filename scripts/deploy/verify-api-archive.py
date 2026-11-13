"""Reject local fixture classes in a normal/deployable Spring Boot archive."""
import sys
import zipfile
from pathlib import Path


def verify(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if 'BOOT-INF/classes/com/tagme/TagmeApplication.class' not in names:
            raise ValueError('Expected the TAG ME Spring Boot application archive')
        for name in names:
            if (name.startswith('BOOT-INF/classes/com/tagme/devfixture/')
                    or name.startswith('BOOT-INF/classes/com/tagme/auth/DevFixture')):
                raise ValueError('Local fixture code is forbidden in a deployable API archive')


if __name__ == '__main__':
    try:
        if len(sys.argv) != 2:
            raise ValueError('Usage: verify-api-archive.py <app.jar>')
        verify(Path(sys.argv[1]))
    except (ValueError, OSError, zipfile.BadZipFile) as failure:
        sys.exit(str(failure))
    print('PASS deployable API archive excludes local fixture code')
