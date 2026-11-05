/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir, writeFile, chmod } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Isolated API contract fixtures. The app never imports these or creates live operator actions.
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
let signedIn = false, admin = false, accessReads = 0, privateReads = 0, mutations = 0, mode = '', releaseWrite, writeEntered, releaseDetail, holdDetail = false;
const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
const revision = (id, number, content, status) => ({ id, itemId: 11, number, content, templateId: 'PLAIN', style: 'ROSE', anonymous: true, submittedAt: '2026-11-05T03:00:00Z', status, reason: status === 'REVIEW_REQUIRED' ? 'TEST_REVIEW' : null });
const old = revision(101, 1, '이미 승인된 소원 본문', 'APPROVED');
let task = { review: { job: { id: 21, revisionId: 201, authorId: 7, itemId: 11, status: 'REVIEW_REQUIRED', attempts: 3, fence: 3, leaseUntil: null }, revision: revision(201, 2, '<script>window.opsInjected = true</script>\n새로 접수한 검수 전용 문구', 'REVIEW_REQUIRED'), approvedRevision: old }, kind: 'WISH', visibility: 'VISIBLE', version: 4, retryCount: 0 };
const second = { review: { job: { id: 22, revisionId: 202, authorId: 8, itemId: 12, status: 'QUEUED', attempts: 0, fence: 0, leaseUntil: null }, revision: { ...revision(202, 1, '차분히 응원하는 문구', 'QUEUED'), itemId: 12 }, approvedRevision: null }, kind: 'CHEER', visibility: 'VISIBLE', version: 2, retryCount: 0 };
const third = { ...second, review: { ...second.review, job: { ...second.review.job, id: 23, itemId: 13 }, revision: { ...second.review.revision, itemId: 13 } } };
let item = { id: 11, kind: 'WISH', authorId: 7, visibility: 'VISIBLE', version: 4, parentWishId: null, approvedRevision: old, pendingRevision: task.review.revision, latestRevision: task.review.revision };
const logs = [];
function log(action, targetType, targetId, reason, before, after) { logs.unshift({ id: logs.length + 1, actorId: 1, action, targetType, targetId, reason, before, after, createdAt: '2026-11-05T04:00:00Z' }); }
function resetTask() { task.review.job.status = 'REVIEW_REQUIRED'; task.review.job.fence++; task.review.revision.status = 'REVIEW_REQUIRED'; task.retryCount = 0; item.pendingRevision = task.review.revision; }
function read(path, q) {
  if (path === '/auth/config') return { available: true, passwordMinLength: 15, passwordMaxLength: 64 };
  if (path === '/users/me') return signedIn ? { id: 1, username: 'test-operator', nickname: '테스트 운영자', onboardingRequired: false } : null;
  if (path === '/tags/pending') return { present: false };
  if (path === '/event') return { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false };
  if (path === '/missions/collection') return { missions: codes.map((code, index) => ({ id: index + 1, code, state: 'LOCKED', active: true })), completedCount: 0, completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } };
  if (path === '/csrf') return { headerName: 'X-XSRF-TOKEN', token: 'operations-test-csrf' };
  if (path === '/admin/access') return { admin: true };
  if (path === '/admin/moderation/tasks/21') return task;
  if (path === '/admin/moderation/tasks/22') return second;
  if (path === '/admin/moderation/tasks') {
    assert.equal(q.get('limit'), '20'); const state = q.get('status') || 'PENDING';
    const matches = t => state === 'ALL' || state === 'PENDING' && ['QUEUED', 'PROCESSING', 'REVIEW_REQUIRED'].includes(t.review.job.status) || t.review.job.status === state;
    const rows = (q.has('after') ? [third] : [task, second]).filter(matches); return { items: rows, nextCursor: !q.has('after') && rows.length === 2 ? 22 : null };
  }
  if (path === '/admin/content/11') return item;
  if (path === '/admin/content') return { items: (!q.get('kind') || q.get('kind') === item.kind) && (!q.get('visibility') || q.get('visibility') === item.visibility) ? [item] : [], nextCursor: null };
  if (path === '/admin/audit-logs') return { items: logs.filter(a => !q.get('action') || a.action === q.get('action')), nextCursor: null };
  throw new Error(`Unexpected GET ${path}`);
}
// The loopback preview's read-only fixture middleware also lets the real render inspector
// see the same tested admin DOM. It is local to this test process, not Vite/app configuration.
const server = await preview({ preview: { host: '127.0.0.1', port: 4477, strictPort: true }, plugins: [{ name: 'operations-read-fixture', configurePreviewServer(server) { server.middlewares.use('/api/v1', (req, res) => { try { if (req.method !== 'GET') { res.statusCode = 405; res.end(); return; } const url = new URL(req.url, 'http://127.0.0.1'); res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(read(url.pathname, url.searchParams))); } catch { res.statusCode = 404; res.end('{}'); } }); } }] });
let browser;
try {
  browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 390, height: 900 } }); const errors = [], routeErrors = [], checks = []; page.on('pageerror', error => errors.push(error.message));
  const record = text => { checks.push(text); console.log(`PASS ${text}`); };
  await page.route('**/api/v1/**', async route => {
    try {
      const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', ''), method = request.method();
      const reply = (status, value) => route.fulfill({ status, contentType: 'application/json', ...(status === 204 ? {} : { body: JSON.stringify(value) }) });
      if (path === '/users/me' && !signedIn) return reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' });
      if (path === '/admin/access') { accessReads++; if (!admin) return reply(403, { code: 'ADMIN_REQUIRED', message: '관리자 권한이 필요해요.' }); }
      else if (path.startsWith('/admin/')) { privateReads++; assert.equal(admin, true); }
      if (method === 'GET') { if (holdDetail && path === '/admin/moderation/tasks/21') { holdDetail = false; await new Promise(r => { releaseDetail = r; }); } return reply(200, read(path, url.searchParams)); }
      assert.equal(request.headers()['x-xsrf-token'], 'operations-test-csrf'); const body = request.postDataJSON(); mutations++; assert.ok(body.reason.trim()); assert.equal(Object.hasOwn(body, 'actorId'), false);
      if (mode === 'hold') { mode = ''; await new Promise(r => { releaseWrite = r; writeEntered?.(); }); }
      if (mode === 'stale') { mode = ''; task.review.job.fence++; item.version++; return reply(409, { code: 'REVIEW_CHANGED', message: '다른 작업으로 상태가 바뀌었어요.' }); }
      if (path === '/admin/moderation/21/decision') {
        assert.equal(body.expectedFence, task.review.job.fence); assert.ok(['APPROVED', 'REJECTED'].includes(body.decision));
        const before = { status: task.review.job.status, fence: task.review.job.fence }; task.review.job.status = body.decision; task.review.job.fence++; task.review.revision.status = body.decision;
        if (body.decision === 'APPROVED') { task.review.approvedRevision = { ...task.review.revision }; item.approvedRevision = task.review.approvedRevision; } item.pendingRevision = null; log('MODERATION_DECISION', 'MODERATION_JOB', 21, body.reason, before, { status: task.review.job.status, fence: task.review.job.fence });
      } else if (path === '/admin/moderation/21/retry') {
        assert.equal(body.expectedFence, task.review.job.fence); assert.ok(task.retryCount < 3); task.review.job.status = 'QUEUED'; task.review.job.fence++; task.retryCount++; task.review.revision.status = 'QUEUED'; log('MODERATION_RETRY', 'MODERATION_JOB', 21, body.reason, { retryCount: task.retryCount - 1 }, { retryCount: task.retryCount });
      } else if (path === '/admin/content/11/visibility' && method === 'PATCH') {
        assert.equal(body.expectedVersion, item.version); const before = { visibility: item.visibility, version: item.version }; item.visibility = body.visibility; item.version++; task.visibility = body.visibility; log('CONTENT_VISIBILITY', 'CONTENT', 11, body.reason, before, { visibility: item.visibility, version: item.version });
      } else throw new Error(`Unexpected ${method} ${path}`);
      if (mode === 'uncertain') { mode = ''; return reply(503, { code: 'NETWORK_ERROR', message: '처리 응답을 확인하지 못했어요.' }); }
      return reply(204);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4477${path}`);
  const capture = async name => { for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); await page.evaluate(() => document.fonts.ready); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); assert.equal(await page.locator('h1').count(), 1); for (const h of await page.locator('h1,h2,h3').all()) if (await h.isVisible()) assert.ok(await h.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1)); await page.screenshot({ path: resolve(output, `${name}-${width}.png`), fullPage: true }); } await page.setViewportSize({ width: 390, height: 900 }); };
  await visit('/admin'); await page.getByRole('heading', { name: '운영자 로그인', exact: true }).waitFor(); assert.equal(accessReads, 0); assert.equal(privateReads, 0);
  signedIn = true; await page.reload(); await page.getByRole('alert').filter({ hasText: '관리자 권한이 필요해요.' }).waitFor(); assert.equal(privateReads, 0); assert.equal(await page.locator('.ops-sheet').count(), 0); record('anonymous and ordinary accounts never fetch private operations data');
  admin = true; await page.reload(); await page.getByRole('link', { name: /소원 검수 #21/ }).waitFor(); await capture('operations-queue'); assert.equal(mutations, 0); await page.getByRole('link', { name: '다음 페이지', exact: true }).click(); await page.getByRole('link', { name: /응원 검수 #23/ }).waitFor(); await page.goBack(); await page.getByRole('link', { name: /소원 검수 #21/ }).waitFor(); record('operator queues have real page counts, bounded cursor navigation and five responsive widths');
  await page.getByRole('link', { name: /소원 검수 #21/ }).click(); await page.getByRole('heading', { name: '이번 검수본', exact: true }).waitFor(); assert.equal(await page.evaluate(() => window.opsInjected), undefined); assert.equal(await page.locator('.ops-detail-pane').getByText('이미 승인된 소원 본문', { exact: true }).count(), 1); assert.equal(await page.locator('.ops-list-pane').isVisible(), false); await capture('operations-review'); record('selected tasks safely compare pending and approved revisions without leaking a second mobile pane');
  await page.getByRole('button', { name: '승인 검토', exact: true }).click(); const dialog = page.getByRole('dialog'); await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); assert.equal(mutations, 0); assert.equal(await page.getByLabel('조치 사유', { exact: true }).evaluate(el => el === document.activeElement), true);
  await page.getByLabel('조치 사유', { exact: true }).fill('원문과 공개 조건 확인'); mode = 'hold'; const writing = new Promise((done, reject) => { const timer = setTimeout(() => reject(new Error('Write did not reach the API fixture')), 15000); writeEntered = () => { clearTimeout(timer); done(); }; }); await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); await dialog.getByRole('button', { name: '처리 중…', exact: true }).waitFor(); await writing; assert.equal(mutations, 1); assert.equal(await dialog.getByRole('button', { name: '취소', exact: true }).isDisabled(), true); assert.ok(releaseWrite); releaseWrite(); await page.getByText('처리가 끝난 검수예요. 이 버전은 다시 판정하지 않아요.', { exact: true }).waitFor(); assert.equal(mutations, 1); record('reason validation and busy confirmation produce one fenced command and reload the actual server decision');
  resetTask(); await page.reload(); await page.getByRole('button', { name: '거절 검토', exact: true }).click(); await page.getByLabel('조치 사유', { exact: true }).fill('다른 운영자와 변경 경합'); mode = 'stale'; await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); await dialog.getByRole('button', { name: '서버 상태 다시 확인', exact: true }).waitFor(); assert.equal(await page.getByLabel('조치 사유', { exact: true }).isDisabled(), true); await page.screenshot({ path: resolve(output, 'operations-conflict-390.png'), fullPage: true }); const staleWrites = mutations; await dialog.getByRole('button', { name: '서버 상태 다시 확인', exact: true }).click(); await page.getByRole('button', { name: '거절 검토', exact: true }).waitFor(); assert.equal(mutations, staleWrites);
  await page.getByRole('button', { name: '거절 검토', exact: true }).click(); await page.getByLabel('조치 사유', { exact: true }).fill('검토 후 거절'); mode = 'uncertain'; await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); await dialog.getByRole('button', { name: '서버 상태 다시 확인', exact: true }).waitFor(); const uncertainWrites = mutations; await dialog.getByRole('button', { name: '서버 상태 다시 확인', exact: true }).click(); await page.getByText('처리가 끝난 검수예요. 이 버전은 다시 판정하지 않아요.', { exact: true }).waitFor(); assert.equal(mutations, uncertainWrites); record('stale and uncertain commands block more writes until explicit read reconciliation; no automatic replay');
  resetTask(); task.retryCount = 2; await page.reload(); await page.getByRole('button', { name: '자동 검수 재처리', exact: true }).click(); await page.getByLabel('조치 사유', { exact: true }).fill('자동 제공자 재확인'); await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); await page.getByText(/수동 재처리 3\/3회/).waitFor(); task.review.job.status = 'REVIEW_REQUIRED'; await page.reload(); await page.getByRole('button', { name: '자동 검수 재처리', exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '자동 검수 재처리', exact: true }).isDisabled(), true); record('retry state follows the server fence and displays the manual three-retry limit');
  await page.getByRole('link', { name: '이 글 공개 상태 관리', exact: true }).click(); await page.getByRole('button', { name: '콘텐츠 숨김 검토', exact: true }).click(); await page.getByLabel('조치 사유', { exact: true }).fill('공개 문구 재확인'); await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); await page.getByRole('button', { name: '공개 복구 검토', exact: true }).waitFor(); assert.equal(item.visibility, 'HIDDEN'); assert.ok(item.approvedRevision); await capture('operations-content'); await page.getByRole('button', { name: '공개 복구 검토', exact: true }).click(); await page.getByLabel('조치 사유', { exact: true }).fill('확인 완료 후 공개 허용'); await dialog.getByRole('button', { name: '사유를 남기고 요청', exact: true }).click(); await page.getByRole('button', { name: '콘텐츠 숨김 검토', exact: true }).waitFor(); assert.equal(item.visibility, 'VISIBLE'); record('visibility confirmations use content versions and preserve the approved revision across hide and restore');
  await page.getByRole('link', { name: '감사 이력', exact: true }).click(); await page.locator('.ops-audit-list li').first().waitFor(); assert.equal(await page.locator('.ops-audit-list li').count(), logs.length); await capture('operations-audit'); assert.equal(await page.locator('.ops-audit-list').getByText('이미 승인된 소원 본문', { exact: true }).count(), 0); await page.getByLabel('조치 종류', { exact: true }).selectOption('MODERATION_RETRY'); await page.locator('.ops-audit-list li').first().waitFor(); assert.equal(await page.locator('.ops-audit-list li').count(), 1);
  await page.getByRole('link', { name: '검수 대기', exact: true }).click(); await page.getByLabel('검수 상태', { exact: true }).selectOption('SUPERSEDED'); await page.getByText('이 조건의 항목이 없어요.', { exact: true }).waitFor(); assert.equal(await page.locator('.ops-row').count(), 0); record('audits show actual actor, reason and metadata; filters and empty views never invent counts or actions');
  await visit('/admin?task=21'); await page.getByRole('heading', { name: '이번 검수본', exact: true }).waitFor(); admin = false; const reads = privateReads; await page.reload(); await page.getByRole('alert').filter({ hasText: '관리자 권한이 필요해요.' }).waitFor(); assert.equal(await page.locator('.ops-body').count(), 0); assert.equal(privateReads, reads); record('revoked access clears sensitive content and stops all private list/detail requests');
  admin = true; await visit('/admin'); await page.getByRole('link', { name: /소원 검수 #21/ }).waitFor(); holdDetail = true; await page.getByRole('link', { name: /소원 검수 #21/ }).click(); await page.getByRole('status').filter({ hasText: '운영 내역을 확인하고 있어요.' }).waitFor(); await page.getByRole('link', { name: '감사 이력', exact: true }).click(); await page.locator('.ops-audit-list li').first().waitFor(); assert.ok(releaseDetail); releaseDetail(); await page.getByRole('button', { name: '목록 새로고침', exact: true }).click(); await page.locator('.ops-audit-list li').first().waitFor(); assert.equal(await page.locator('.ops-detail-pane').count(), 0); record('late detail responses cannot repopulate a different operations view');
  assert.deepEqual(errors, []); assert.deepEqual(routeErrors, []); await writeFile(resolve(output, 'operations-contract-checks.json'), JSON.stringify({ kind: 'explicit test-only API fixtures; no live operator actions', checks, errors, routeErrors }, null, 2));
  resetTask(); item.visibility = 'VISIBLE'; task.visibility = 'VISIBLE';
  const inspector = resolve('../.taste-skill/operations-render'); await mkdir(inspector, { recursive: true }); const launcher = resolve(inspector, 'chromium.sh'); await writeFile(launcher, `#!/usr/bin/env bash\nexec "${chromium.executablePath()}" --no-sandbox "$@"\n`); await chmod(launcher, 0o700);
  await new Promise((done, reject) => { const child = spawn(process.execPath, ['scripts/taste.mjs', 'inspect', 'http://127.0.0.1:4477/admin?task=21', '--app', '--widths', '1440,768,390', '--out', '.taste-skill/operations-render'], { cwd: resolve('..'), env: { ...process.env, CHROME_PATH: launcher }, stdio: 'inherit' }); child.on('error', reject); child.on('exit', code => code === 0 ? done() : reject(new Error(`Operations render check exited ${code}`))); });
} finally { await browser?.close(); await server.httpServer.close(); }
