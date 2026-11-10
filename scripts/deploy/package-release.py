"""Package only public runtime code; secrets never enter a deployment release."""
import argparse
import hashlib
import io
import json
import re
import tarfile
from pathlib import Path

FILES = (
    'compose.yml', 'compose.prod.yml', 'compose.small.yml', 'compose.images.yml',
    '.env.production.example', 'infra/caddy/Caddyfile.prod', 'infra/ec2/deploy.sh',
    'infra/ec2/validate-env.py', 'infra/ec2/poll-release.py', 'scripts/smoke.sh',
)


def bundle(root):
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
        for name in FILES:
            path = root / name
            if path.is_symlink() or not path.is_file():
                raise ValueError(f'Expected regular release file: {name}')
            data = path.read_bytes().replace(b'\r\n', b'\n')
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mode = 0o600
            archive.addfile(info, io.BytesIO(data))
    return buffer.getvalue()


def checksum(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image-tag', required=True)
    parser.add_argument('--run-id', required=True, type=int)
    parser.add_argument('--run-attempt', required=True, type=int)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r'[a-f0-9]{40}-[0-9]+-[0-9]+', args.image_tag):
        parser.error('Invalid immutable image tag')
    sha, run_id, attempt = args.image_tag.split('-')
    if (int(run_id), int(attempt)) != (args.run_id, args.run_attempt):
        parser.error('Image tag must identify this workflow run and attempt')
    root = Path(__file__).resolve().parents[2]
    args.output.mkdir(parents=True, exist_ok=True)
    runtime = args.output / 'runtime.tar.gz'
    runtime.write_bytes(bundle(root))
    manifest = dict(schema=1, commit_sha=sha, image_tag=args.image_tag,
                    workflow_run_id=args.run_id, workflow_run_attempt=args.run_attempt,
                    runtime_sha256=checksum(runtime), images_sha256=checksum(args.output / 'images.tar.gz'))
    (args.output / 'release.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    (args.output / 'notes.md').write_text(
        f'Runtime images built from main commit {sha}.\n\n'
        f'GitHub Actions run: https://github.com/anonymity-developer/connection_16/actions/runs/{args.run_id}\n\n'
        'No runtime credentials or participant data are included.\n', encoding='utf-8')
    print(f'Packaged {len(FILES)} public runtime files and verified image checksums.')


if __name__ == '__main__':
    main()
