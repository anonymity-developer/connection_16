#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backup_dir="${repo_root}/backups"
mkdir -p "$backup_dir"
backup_path="${backup_dir}/tagme-$(date -u +%Y%m%dT%H%M%SZ)-$$.sql.gz"
temporary_path="${backup_path}.tmp"
trap 'rm -f "$temporary_path"' EXIT

docker compose --project-directory "$repo_root" -f "$repo_root/compose.yml" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  | gzip > "$temporary_path"
gzip -t "$temporary_path"
mv "$temporary_path" "$backup_path"
echo "Backup created: $backup_path"
