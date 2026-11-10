#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

release_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
registry="${1:?ECR registry required}"
image_tag="${2:?Immutable image tag required}"
[[ "$registry" =~ ^[0-9]{12}\.dkr\.ecr\.ap-southeast-2\.amazonaws\.com$ ]] || exit 2
[[ "$image_tag" =~ ^[a-f0-9]{40}-[0-9]+-[0-9]+$ ]] || exit 2
[[ "$release_dir" == "/opt/tagme/releases/$image_tag" ]] || exit 2
install -d -m 0700 /opt/tagme/backups
exec 9>/opt/tagme/deploy.lock
flock -n 9 || { echo 'Another deployment holds the host lock.' >&2; exit 1; }

export AWS_DEFAULT_REGION=ap-southeast-2
export TAGME_REGISTRY="$registry" TAGME_IMAGE_TAG="$image_tag"
export DOCKER_CONFIG
DOCKER_CONFIG="$(mktemp -d)"
env_tmp="$(mktemp /opt/tagme/.env.XXXXXX)"
trap 'rm -f "$env_tmp"; rm -rf "$DOCKER_CONFIG"' EXIT
# Never echo/source the dotenv file or put its contents in Run Command parameters.
aws ssm get-parameter --name /tagme/production/env --with-decryption \
  --query Parameter.Value --output text > "$env_tmp"
domain="$(python3 "$release_dir/infra/ec2/validate-env.py" "$env_tmp")"

compose=(docker compose --project-name tagme-prod --project-directory "$release_dir"
  --env-file "$env_tmp" -f "$release_dir/compose.yml")
if [[ -n "$domain" ]]; then
  compose+=(-f "$release_dir/compose.prod.yml")
fi
compose+=(-f "$release_dir/compose.small.yml" -f "$release_dir/compose.images.yml" --profile app)
"${compose[@]}" config --quiet
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry" >/dev/null
# Finish pulls before touching running services.
"${compose[@]}" pull

mysql_id="$(docker ps -q --filter label=com.docker.compose.project=tagme-prod --filter label=com.docker.compose.service=mysql)"
existing_mysql="$(docker ps -aq --filter label=com.docker.compose.project=tagme-prod --filter label=com.docker.compose.service=mysql)"
if [[ -n "$existing_mysql" && -z "$mysql_id" ]]; then
  echo 'Existing MySQL is stopped; recover it and take a backup before deployment.' >&2
  exit 1
fi
if [[ -n "$mysql_id" ]]; then
  backup="/opt/tagme/backups/predeploy-$(date -u +%Y%m%dT%H%M%SZ)-$image_tag.sql.gz"
  docker exec "$mysql_id" sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
    | gzip > "$backup.tmp"
  gzip -t "$backup.tmp"
  mv "$backup.tmp" "$backup"
fi

# Migration failures stop here. Do not automatically roll back a database migration.
"${compose[@]}" up -d --no-build --pull never --wait --wait-timeout 240
"${compose[@]}" exec -T web wget -qO- http://127.0.0.1/api/v1/meta >/dev/null 2>&1 || {
  # Production Caddy uses its internal probe; public HTTPS is checked separately below.
  [[ -n "$domain" ]] || exit 1
  "${compose[@]}" exec -T api curl -fsS http://127.0.0.1:8080/api/v1/meta >/dev/null
}
if [[ -n "$domain" ]]; then
  bash "$release_dir/scripts/smoke.sh" "https://$domain"
else
  echo 'Internal deployment: no published ports; DNS/HTTPS and public login remain pending.'
fi

mv "$env_tmp" /opt/tagme/.env.production
printf 'TAGME_REGISTRY=%s\nTAGME_IMAGE_TAG=%s\n' "$registry" "$image_tag" > "$release_dir/images.env"
ln -sfn "$release_dir" /opt/tagme/current.next
mv -Tf /opt/tagme/current.next /opt/tagme/current
echo "Healthy release: $image_tag"
