"""Send an allowlisted, secret-free release bundle via SSM using the AWS CLI."""
import argparse
import base64
import io
import json
import re
import subprocess
import tarfile
import time
from pathlib import Path

FILES = (
    'compose.yml', 'compose.prod.yml', 'compose.small.yml', 'compose.images.yml',
    '.env.production.example', 'infra/caddy/Caddyfile.prod', 'infra/ec2/deploy.sh',
    'infra/ec2/validate-env.py', 'scripts/smoke.sh',
)


def parameters(root, registry, tag):
    if not re.fullmatch(r'[0-9]{12}\.dkr\.ecr\.ap-southeast-2\.amazonaws\.com', registry):
        raise ValueError('Invalid Sydney ECR registry')
    if not re.fullmatch(r'[a-f0-9]{40}-[0-9]+-[0-9]+', tag):
        raise ValueError('Invalid immutable image tag')
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode='w:gz') as archive:
        for name in FILES:
            path = root / name
            if path.is_symlink() or not path.is_file():
                raise ValueError(f'Expected regular release file: {name}')
            # Normalize CRLF even when a Windows worktree creates the release.
            data = path.read_bytes().replace(b'\r\n', b'\n')
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mode = 0o600
            archive.addfile(info, io.BytesIO(data))
    encoded = base64.b64encode(buffer.getvalue()).decode('ascii')
    directory = f'/opt/tagme/releases/{tag}'
    command = (
        'set -eu\numask 077\n'
        f'mkdir -p {directory}\n'
        f"printf '%s' '{encoded}' | base64 -d | tar -xz -C {directory}\n"
        f'bash {directory}/infra/ec2/deploy.sh {registry} {tag}'
    )
    if len(command.encode()) > 20000:
        raise ValueError('Release bundle exceeds the bounded SSM command size')
    return {'commands': [command], 'executionTimeout': ['1200']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance-id', required=True)
    parser.add_argument('--registry', required=True)
    parser.add_argument('--image-tag', required=True)
    parser.add_argument('--aws-cli', default='aws')
    parser.add_argument('--profile')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(r'i-[a-f0-9]{17}', args.instance_id):
        parser.error('Expected an EC2 instance ID')
    root = Path(__file__).resolve().parents[2]
    payload = {
        'DocumentName': 'AWS-RunShellScript', 'InstanceIds': [args.instance_id],
        'Parameters': parameters(root, args.registry, args.image_tag),
        'TimeoutSeconds': 120,
        'Comment': f'TAG ME release {args.image_tag}',
    }
    if args.dry_run:
        print(f'Release bundle validated: {len(FILES)} files, no runtime environment files')
        return
    cli = [args.aws_cli, '--region', 'ap-southeast-2', '--no-cli-pager', '--output', 'json']
    if args.profile:
        cli += ['--profile', args.profile]

    def call(*command):
        result = subprocess.run(cli + list(command), text=True, encoding='utf-8', capture_output=True)
        if result.returncode:
            raise RuntimeError('AWS CLI request failed; inspect AWS authorization and SSM status')
        return json.loads(result.stdout)

    result = call('ssm', 'send-command', '--cli-input-json', json.dumps(payload))
    command_id = result['Command']['CommandId']
    print(f'SSM command: {command_id}', flush=True)
    deadline = time.monotonic() + 1320
    while time.monotonic() < deadline:
        time.sleep(10)
        # Invocation propagation can lag SendCommand. Retry that case only.
        response = subprocess.run(cli + ['ssm', 'get-command-invocation', '--command-id', command_id,
                                         '--instance-id', args.instance_id],
                                  text=True, encoding='utf-8', capture_output=True)
        if response.returncode:
            if 'InvocationDoesNotExist' in response.stderr:
                continue
            raise RuntimeError(f'Cannot inspect SSM command {command_id}')
        invocation = json.loads(response.stdout)
        status = invocation['Status']
        if status == 'Success':
            print('EC2 release and readiness checks succeeded.')
            return
        if status not in ('Pending', 'InProgress', 'Delayed'):
            # Do not mirror remote logs that might contain future app secrets/PII into public CI.
            raise RuntimeError(f'SSM command {command_id}: {status}; inspect restricted AWS command output')
    raise TimeoutError(f'SSM command {command_id} did not finish in time; inspect it before retrying')


if __name__ == '__main__':
    main()
