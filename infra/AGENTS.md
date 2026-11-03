# Infrastructure and operations

- Follow root AGENTS.md. Use the existing common/development/production Compose separation; retain a single VM deployment unless measurements justify a change.
- Production exposes Caddy 80/443 only. Do not publish API/MySQL/Redis externally or combine development port overrides with production.
- Never add secrets to Docker build args, browser VITE variables, source, skill files or logs. Preserve example placeholders and loopback development bindings.
- Docker volumes are not backups. Validate a server-external backup and restore procedure; do not use `down -v` against persistent user data. CI's explicitly ephemeral teardown is separate.
- Before deployment check migrations/backup/previous image and readiness. Deployment is not authorized merely because an implementation phase or skill setup was requested.
- Validate both configurations from the root:
  - `docker compose -f compose.yml -f compose.dev.yml --profile app config --quiet`
  - `docker compose -f compose.yml -f compose.prod.yml --profile app config --quiet`
- Supply non-secret test environment values required by Compose; never substitute actual keys into tracked files.
- With Docker available, start the development stack with `--build --wait` and run `bash scripts/smoke.sh http://localhost:8088`.
- If Docker/daemon, external Mattermost/AI permissions or server access are unavailable, record the missing prerequisite and continue independent work. Do not mark deployment/integration verified.
