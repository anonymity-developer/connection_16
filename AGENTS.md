# TAG ME — Codex project guidance

## Start here

- This is the 사이드프로젝트 project: TAG ME, an SSAFY 16th Seoul networking event.
- Read `docs/progress.md` for the current phase, then only the relevant documents routed by `docs/README.md`.
- Inspect `git status` before editing. Preserve unrelated work. Use the existing React/TypeScript + Java 21/Spring Boot + MySQL/Redis/Compose stack and npm lockfile.
- State which phase and skill you are using. Continue authorized implementation, tests, documentation and commits without repeatedly requesting permission. Deployment and person-directed messages require their own user authorization.
- Do not delegate to subagents unless the user or an applicable existing instruction explicitly requests delegation.

## Skills: load the actual files

| Task | Read and use |
| --- | --- |
| Build/restyle/review UI | `.agents/skills/design-taste-frontend/SKILL.md`; for collection/auth/admin/app screens also read its `references/app-screens.md` in full |
| Real browser interactions/screenshots | `.agents/skills/playwright/SKILL.md`; use terminal CLI when available, or the connected browser capability if this runtime cannot launch it |
| Failing GitHub Actions checks | `.agents/skills/gh-fix-ci/SKILL.md`; use authenticated GitHub tools when `gh` is not authenticated |
| Backend/auth/data changes | `backend/AGENTS.md`, `docs/auth.md`, `docs/api.md`, `docs/data-model.md`, `docs/erd.md` as relevant |
| Container/release changes | `infra/AGENTS.md`, `docs/development.md`, `docs/operations.md` |

These are pinned repository copies, not links to instructions you should assume you know. Load SKILL.md before using a skill; do not load entire reference libraries. If this session's skill list is cached, read these paths explicitly. User requirements and project business rules override stylistic examples or generic workflows from upstream skills.

## Non-negotiable product rules

- Login ID is the Mattermost username; the password is set in TAG ME at signup. The immutable external user ID remains the account linkage key. Never ask for or proxy a Mattermost password.
- DM OTP is exactly six numeric characters, including leading zeros, for SIGN_UP/PASSWORD_CHANGE/PASSWORD_RESET. Bind challenges to account/purpose; never implement OTP-only ordinary login or a fixed shared code.
- Credentials, username storage and business APIs are implemented; follow current contracts and migrations. Password hashes only; invalidate previous sessions on password changes/resets.
- NFC/QR uses a 43-character Base64URL token from 32 random bytes; persist only its hash. Authenticate and check server-side unlock on the four NFC missions. SHOW_ME is granted from an eligible profile during OPEN without NFC; use the DB processing clock for its evidence and completion. Never treat a route, UI state or fixture as authorization.
- Distinguish the two S missions by code. One official slot per wish/cheer/game, one initial SYNC result. All mission completion uses one transactional path with DB constraints and consistent row locking.
- Import Mattermost name/region/class/photo; TAG ME nickname is required to save a profile, interests/bio/links are optional for onboarding. The SHOW_ME reward additionally requires a nonblank bio and at least one interest. No participant private-profile toggle. Follow `docs/participation.md` and `docs/mattermost-profile.md`; do not reintroduce direct identity input.
- Keep moderation status, published revision, mission completion, profile visibility and award eligibility separate. Operational completion order is not final award rank.
- Never invent participants, production results, awards, event dates, SYNC questions or external integration success. Dev fixtures must not enter the production build or create authentication/official records.

## Checks and change delivery

- Setup: `node scripts/codex-doctor.mjs`; source integrity: `node scripts/verify-skills.mjs`.
- Frontend changes: `cd frontend && npm run lint && npm run build`. Add/run meaningful behavior tests when changing interactions. Follow `frontend/AGENTS.md` for visual checks.
- Backend changes: `cd backend && ./gradlew test bootJar`; data/concurrency changes also require `./gradlew integrationTest` with Docker.
- Container changes: validate both Compose overrides and run the full stack smoke where Docker is available. See `infra/AGENTS.md`.
- Documentation changes: check relative links, examples and `git diff --check`. Keep implemented state and intended design distinct; update `docs/progress.md` after each phase.
- Report exactly which checks passed, failed or were blocked. Installed files and successful script startup are not a successful visual audit. Do not weaken gates to make them green.
- Stage files explicitly by purpose. Keep agent setup and unfinished feature work in separate commits. Check remote head before updating it; do not overwrite concurrent changes or force-push.

## Code Review Rules

- Flag authentication bypasses, account/purpose confusion, plaintext credentials or secret/PII leakage, missing CSRF/ownership/unlock checks, fake completion, and lost one-time constraints.
- Flag moderation version races, incorrect award time/order, profile privacy races, inconsistent lock ordering, destructive migration edits and untested production claims.
- Never commit `.env`, credentials, actual tag URLs, DB dumps, logs, screenshots with personal data, generated browser profiles or build output.
