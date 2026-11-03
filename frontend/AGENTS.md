# Frontend implementation

- Follow root AGENTS.md. Before UI work read `../.agents/skills/design-taste-frontend/SKILL.md` and select its page or app route. TAG ME collection, authentication and missions are app screens; a separate public marketing landing page uses the page route.
- Read `docs/ui-ux.md`, relevant `docs/user-flows.md` and `docs/auth.md` from the repository root. Existing unreviewed styling is not a confirmed brand; do not use `--keep` merely to protect it from the skill's critique.
- Use existing React/TypeScript/Vite and CSS. Check package.json before adding dependencies. Preserve semantic links/buttons, browser back/forward, focus, labels, touch targets, safe areas and reduced motion.
- Use CSS tokens for shared colors/type/spacing. Keep copy in Korean and retain the five exact mission names. Do not add stock testimonials, fabricated event information or unrelated imagery just to satisfy a template.
- Keep dev fixtures behind `import.meta.env.DEV`; check the built application with preview parameters and ensure they do not activate. Never submit real credentials or create fake sessions for a visual prototype.
- Do not report phase 1 complete based on lint/build. Inspect the **production build**, test 360/390/430 plus desktop/tablet widths, and view screenshots. Test validation/pasting leading-zero OTP, password visibility, route changes, dialog Escape/focus and direct locked mission access.
- Taste: use `node scripts/taste.mjs draw ...`, `lint ...`, `inspect ... --app`; run from repository root. Read its app reference before drawing an app direction. Keep output in ignored `.taste-skill/`.
- Browser: use the vendored Playwright instructions when CLI/auth/environment permits. If browser launch is blocked, record the exact limitation and use an available browser tool; do not mark the visual gate passed.
- After behavior changes run `npm run lint`, `npm run build` and relevant meaningful tests. Only claim checks actually executed. UI preview success is not server authentication, moderation, completion or persistence.
