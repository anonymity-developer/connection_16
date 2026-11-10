"""Pull only successful main deployments from this public repository; no GitHub/AWS CI key."""
try:
    import fcntl
except ImportError:
    fcntl = None  # Pure validation functions also run in Windows development tests.
import hashlib
import json
import os
import re
import shutil
import subprocess
import tarfile
import urllib.error
import urllib.request
from pathlib import Path

REPO = 'anonymity-developer/connection_16'
API = f'https://api.github.com/repos/{REPO}'
ROOT = Path('/opt/tagme')
FILES = {
    'compose.yml', 'compose.prod.yml', 'compose.small.yml', 'compose.images.yml',
    '.env.production.example', 'infra/caddy/Caddyfile.prod', 'infra/ec2/deploy.sh',
    'infra/ec2/validate-env.py', 'infra/ec2/poll-release.py', 'scripts/smoke.sh',
}


def get_json(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'tagme-deployment', 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = response.read(2 * 1024 * 1024 + 1)
    if len(data) > 2 * 1024 * 1024:
        raise ValueError('Oversized API response')
    return json.loads(data)


def validate_manifest(manifest, release):
    tag = manifest.get('image_tag', '')
    if manifest.get('schema') != 1 or not re.fullmatch(r'[a-f0-9]{40}-[0-9]+-[0-9]+', tag):
        raise ValueError('Invalid release image tag or schema')
    sha, run, attempt = tag.split('-')
    if (manifest.get('commit_sha'), manifest.get('workflow_run_id'), manifest.get('workflow_run_attempt')) != (sha, int(run), int(attempt)):
        raise ValueError('Release provenance fields do not match')
    if release.get('tag_name') != f'tagme-deploy-{tag}' or release.get('target_commitish') != sha:
        raise ValueError('Release does not target the declared commit')
    if release.get('draft') or release.get('prerelease') or release.get('author', {}).get('login') != 'github-actions[bot]':
        raise ValueError('Only published deployments by GitHub Actions are accepted')
    for key in ('runtime_sha256', 'images_sha256'):
        if not re.fullmatch(r'[a-f0-9]{64}', manifest.get(key, '')):
            raise ValueError('Missing release checksum')
    return tag


def asset(release, name, limit):
    candidates = [a for a in release.get('assets', []) if a.get('name') == name]
    if len(candidates) != 1:
        raise ValueError(f'Expected one asset: {name}')
    result = candidates[0]
    expected = f'https://github.com/{REPO}/releases/download/{release["tag_name"]}/{name}'
    if result.get('browser_download_url') != expected or result.get('uploader', {}).get('login') != 'github-actions[bot]':
        raise ValueError('Unexpected release asset URL or uploader')
    if not 0 < result.get('size', 0) <= limit:
        raise ValueError('Release asset exceeds its size limit')
    return result


def download(info, destination, expected_hash=None):
    request = urllib.request.Request(info['browser_download_url'], headers={'User-Agent': 'tagme-deployment'})
    digest = hashlib.sha256()
    count = 0
    partial = destination.with_suffix(destination.suffix + '.part')
    try:
        with urllib.request.urlopen(request, timeout=120) as response, partial.open('wb') as output:
            while data := response.read(1024 * 1024):
                count += len(data)
                if count > info['size']:
                    raise ValueError('Download exceeded the declared asset size')
                output.write(data)
                digest.update(data)
        if count != info['size'] or (expected_hash and digest.hexdigest() != expected_hash):
            raise ValueError('Release asset checksum/size mismatch')
        partial.replace(destination)
    finally:
        partial.unlink(missing_ok=True)


def extract_runtime(path, directory):
    with tarfile.open(path, 'r:gz') as archive:
        members = archive.getmembers()
        if len(members) != len(FILES) or {m.name for m in members} != FILES:
            raise ValueError('Runtime bundle has unexpected or missing files')
        if any(not m.isfile() or m.size > 256 * 1024 for m in members):
            raise ValueError('Runtime bundle contains non-regular or oversized files')
        for member in members:
            target = directory / member.name
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            target.write_bytes(archive.extractfile(member).read())
            target.chmod(0o600)


def verify_images(path, tag):
    with tarfile.open(path, 'r:gz') as archive:
        member = archive.getmember('manifest.json')
        if not member.isfile() or member.size > 64 * 1024:
            raise ValueError('Invalid Docker image manifest')
        manifest = json.load(archive.extractfile(member))
    tags = [value for item in manifest for value in (item.get('RepoTags') or [])]
    if sorted(tags) != sorted([f'tagme-api:{tag}', f'tagme-web:{tag}']):
        raise ValueError('Image archive must contain exactly the two declared application tags')


def main():
    if fcntl is None:
        raise RuntimeError('The deployment controller requires Linux')
    os.umask(0o077)
    ROOT.mkdir(exist_ok=True, mode=0o700)
    with (ROOT / 'poll.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        try:
            release = get_json(API + '/releases/latest')
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                print('Waiting for the first successful deployment release.')
                return
            raise
        release_tag = release.get('tag_name', '')
        if not re.fullmatch(r'tagme-deploy-[a-f0-9]{40}-[0-9]+-[0-9]+', release_tag):
            raise ValueError('Latest release is not a TAG ME deployment')
        tag = release_tag.removeprefix('tagme-deploy-')
        current = ROOT / 'current'
        if current.is_symlink() and current.resolve().name == tag:
            print('Current deployment is already up to date.')
            return
        directory = ROOT / 'releases' / tag
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        download(asset(release, 'release.json', 64 * 1024), directory / 'release.json')
        manifest = json.loads((directory / 'release.json').read_text())
        validate_manifest(manifest, release)
        sha = manifest['commit_sha']
        if get_json(API + '/git/ref/heads/main')['object']['sha'] != sha:
            print('Waiting for a deployment release for current main.')
            return
        run = get_json(API + f'/actions/runs/{manifest["workflow_run_id"]}')
        if run.get('status') != 'completed':
            print('Waiting for deployment publication workflow to finish.')
            return
        if (run.get('conclusion') != 'success' or run.get('head_sha') != sha or
                run.get('head_branch') != 'main' or run.get('run_attempt') != manifest['workflow_run_attempt'] or
                run.get('path', '').split('@')[0] != '.github/workflows/deploy.yml' or
                run.get('event') not in ('workflow_run', 'workflow_dispatch') or
                run.get('head_repository', {}).get('full_name') != REPO):
            raise ValueError('Deployment workflow provenance check failed')
        ci = get_json(API + f'/actions/workflows/ci.yml/runs?branch=main&head_sha={sha}&status=success&per_page=100')
        if not any(r.get('head_sha') == sha and r.get('event') == 'push' and r.get('conclusion') == 'success'
                   for r in ci.get('workflow_runs', [])):
            raise ValueError('No successful push CI for this main commit')
        images = asset(release, 'images.tar.gz', 1024 * 1024 * 1024)
        if shutil.disk_usage(ROOT).free < images['size'] * 3 + 1024 * 1024 * 1024:
            raise RuntimeError('Insufficient free space for a safe deployment')
        download(asset(release, 'runtime.tar.gz', 1024 * 1024), directory / 'runtime.tar.gz', manifest['runtime_sha256'])
        download(images, directory / 'images.tar.gz', manifest['images_sha256'])
        extract_runtime(directory / 'runtime.tar.gz', directory)
        verify_images(directory / 'images.tar.gz', tag)
        subprocess.run(['docker', 'load', '--input', str(directory / 'images.tar.gz')], check=True)
        subprocess.run(['bash', str(directory / 'infra/ec2/deploy.sh'), tag], check=True)
        # Refresh the controller only after this release passed all health checks.
        shutil.copyfile(directory / 'infra/ec2/poll-release.py', ROOT / 'poll-release.py.next')
        (ROOT / 'poll-release.py.next').chmod(0o700)
        (ROOT / 'poll-release.py.next').replace(ROOT / 'poll-release.py')
        print(f'Deployed successful main release: {tag}')


if __name__ == '__main__':
    main()
