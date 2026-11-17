/* global document, window */
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

const server = await preview({ preview: { host: '127.0.0.1', port: 4482, strictPort: true } });
const output = resolve('../output/playwright/tap-support'); await mkdir(output, { recursive: true });
let browser;
try {
  browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const errors = [], queries = [], writes = [], now = '2026-11-17T01:00:00Z'; let mode = 'lost', applied = false, saved = null, restored = false;
  page.on('pageerror', error => errors.push(error.message));
  const row = (id, status, retryAllowed) => ({ userId: id, attemptId: id * 10, username: `fixture-tap-${id}`, nickname: `검증 참가자 ${id}`, realName: '합성 이름', region: '서울', classNo: 2, status, reason: status === 'INVALID' ? 'DUPLICATE_MILLISECOND' : null, tapCount: 118, startsAt: now, submitDeadline: now, submittedAt: status === 'EXPIRED' ? null : now, resetGeneration: 0, retryAllowed, signals: { codes: ['REGULAR_INTERVALS'], sameTimestampPairs: 0, commonIntervalMs: 20, intervalSharePermille: 900 } });
  const rows = [row(2, 'INVALID', true), row(3, 'EXPIRED', true), row(4, 'VALID', false)].map(row => ({ ...row, recovery: null, recordAccepted: row.status === 'VALID', letterEarned: row.status === 'VALID', rank: row.status === 'VALID' ? 1 : null }));
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.slice('/api/v1'.length);
    const reply = (status, body) => route.fulfill({ status, json: body });
    if (path === '/auth/config') return reply(200, { available: true, passwordMinLength: 8, passwordMaxLength: 20 });
    if (path === '/users/me') return reply(200, { id: 1, username: 'admin:contract', nickname: null, admin: true, onboardingRequired: false });
    if (path === '/tags/pending') return reply(200, { present: false });
    if (path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'tap-support-test-csrf' });
    if (path === '/admin/access') return reply(200, { admin: true });
    if (req.method() === 'GET' && path === '/admin/tap-attempts') {
      queries.push(url.search); let items = rows.filter(row => !applied || row.userId !== 2).map(row => mode === 'restore' && row.userId === 2 ? { ...row, canRestore: true, retryAllowed: false } : row);
      if (restored) items = items.map(row => row.userId !== 2 ? row : { ...row, status: 'VALID', reason: null, canRestore: false, retryAllowed: false, recordAccepted: true, letterEarned: true, rank: 2,
        signals: { ...row.signals, codes: [], sameTimestampPairs: 1, intervalSharePermille: 500 },
        recovery: { originalStatus: 'INVALID', originalReason: 'DUPLICATE_MILLISECOND', originalDeadline: now, effectiveDeadline: now, recognizedAt: now, restoredAt: now, actorId: 1, operatorId: 'admin:contract', reason: '원래 기록과 수집 순위 복구' } });
      if (url.searchParams.get('filter') === 'FAILED') items = items.filter(row => row.status !== 'VALID');
      if (url.searchParams.get('filter') === 'RECOVERED') items = items.filter(row => row.recovery);
      if (url.searchParams.get('q')) items = items.filter(row => row.username.includes(url.searchParams.get('q')));
      return reply(200, { items, total: items.length, offset: 0, nextOffset: null });
    }
    if (req.method() === 'POST' && /\/admin\/tap-attempts\/members\/\d+\/(retry|restore)/.test(path)) {
      assert.equal(req.headers()['x-xsrf-token'], 'tap-support-test-csrf');
      const value = { body: req.postDataJSON(), key: req.headers()['idempotency-key'], path }; writes.push(value);
      if (mode === 'restore') { assert.equal(value.body.confirmation, 'RESTORE'); assert.equal(path, '/admin/tap-attempts/members/2/restore'); restored = true; return reply(200, { attemptId: 20, userId: 2, recognizedAt: now, restoredAt: now }); }
      if (mode === 'conflict') return reply(409, { code: 'TAP_ATTEMPT_CHANGED', message: '현재 도전이 바뀌었어요. 목록을 다시 확인해 주세요.' });
      if (mode === 'denied') return reply(403, { code: 'ADMIN_REQUIRED', message: '관리자 권한이 필요해요.' });
      if (!saved) { saved = value; applied = true; return reply(503, { code: 'NETWORK_ERROR', message: '응답 유실 검사' }); }
      assert.deepEqual(value, saved); return reply(200, { id: 1, generation: 1 });
    }
    errors.push(`Unexpected ${req.method()} ${path}`); return reply(500, { message: 'Unexpected request' });
  });
  const visit = () => page.goto('http://127.0.0.1:4482/admin/tap-support');
  await visit(); await page.getByRole('button', { name: '재도전 허용', exact: true }).first().waitFor();
  assert.match(queries.at(-1), /filter=ALL/); assert.equal(await page.locator('.tap-support tbody tr').count(), 3);
  const normal = page.locator('.tap-support tbody tr').filter({ hasText: 'fixture-tap-4' });
  await normal.getByText('일정 간격 반복', { exact: true }).waitFor(); await normal.getByText('점수: 인정', { exact: true }).waitFor(); await normal.getByText('F: 획득', { exact: true }).waitFor(); await normal.getByText('게임 순위: 1위', { exact: true }).waitFor();
  await page.getByRole('button', { name: '실패한 도전', exact: true }).click(); await page.waitForFunction(() => document.querySelectorAll('.tap-support tbody tr').length === 2);
  for (const width of [360, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false);
    await page.screenshot({ path: resolve(output, `queue-${width}.png`), fullPage: true });
  }
  await page.getByRole('button', { name: '전체 현재 도전', exact: true }).click(); await page.locator('.tap-support tbody tr').nth(2).waitFor();
  assert.equal(await page.locator('.tap-support tbody tr').last().getByRole('button', { name: '재도전 허용', exact: true }).isDisabled(), true);
  await page.getByLabel('아이디·이름·닉네임').fill('fixture-tap-2'); await page.getByRole('button', { name: '검색', exact: true }).click();
  await page.waitForURL(url => url.searchParams.get('q') === 'fixture-tap-2'); await page.waitForFunction(() => document.querySelectorAll('.tap-support tbody tr').length === 1);
  await page.getByRole('button', { name: '재도전 허용', exact: true }).click(); const dialog = page.getByRole('dialog'); await dialog.waitFor();
  await dialog.getByRole('button', { name: '재도전 허용하기' }).click(); assert.equal(writes.length, 0);
  await page.getByLabel('재도전 사유', { exact: true }).fill('기존 동일 밀리초 판정 지원');
  await page.getByLabel('확인란에 ‘재도전’을 입력해 주세요').fill('재도전');
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({ path: resolve(output, `confirm-${width}.png`), fullPage: true }); }
  await dialog.getByRole('button', { name: '재도전 허용하기' }).click(); await dialog.getByRole('button', { name: '같은 요청 다시 확인' }).waitFor();
  assert.equal(await page.getByLabel('재도전 사유', { exact: true }).isDisabled(), true); assert.equal(writes.length, 1);
  await dialog.getByRole('button', { name: '같은 요청 다시 확인' }).click(); await dialog.waitFor({ state: 'hidden' });
  assert.equal(writes.length, 2); assert.deepEqual(writes[0], writes[1]); await page.getByText('F 재도전을 허용했어요. 참가자는 게임 화면을 새로고침해 주세요.', { exact: true }).waitFor();
  assert.equal(writes[0].body.attemptId, 20); assert.equal(writes[0].body.expectedGeneration, 0); assert.equal(writes[0].body.confirmation, 'RETRY');
  applied = false; mode = 'restore'; await visit(); await page.getByRole('button', { name: '기존 기록 복구', exact: true }).click();
  await page.getByLabel('복구 사유', { exact: true }).fill('원래 기록과 수집 순위 복구'); await page.getByLabel('확인란에 ‘복구’를 입력해 주세요').fill('복구');
  await page.getByRole('button', { name: '기존 기록 복구하기', exact: true }).click(); await page.getByRole('dialog').waitFor({ state: 'hidden' });
  await page.getByText('원래 기록으로 F를 복구했어요. 전체 수집 순위도 인정 시각으로 다시 계산돼요.', { exact: true }).waitFor();
  await page.getByRole('button', { name: '복구된 도전', exact: true }).click(); await page.waitForFunction(() => document.querySelectorAll('.tap-support tbody tr').length === 1);
  await page.reload(); const history = page.locator('.tap-support tbody tr').filter({ hasText: 'fixture-tap-2' });
  await history.getByText('기존 반려 복구', { exact: true }).waitFor(); await history.getByText('같은 밀리초 입력으로 무효 처리됨', { exact: true }).waitFor(); await history.getByText('현재 신호 없음', { exact: true }).waitFor();
  await history.getByText('점수: 인정', { exact: true }).waitFor(); await history.getByText('F: 획득', { exact: true }).waitFor(); await history.getByText('게임 순위: 2위', { exact: true }).waitFor();
  assert.equal(await history.getByRole('button', { name: '재도전 허용', exact: true }).isDisabled(), true);
  await history.locator('summary').click(); await history.getByText('admin:contract', { exact: true }).waitFor(); await history.getByText('원래 기록과 수집 순위 복구', { exact: true }).waitFor();
  for (const width of [360,390,430,768,1440]) { await page.setViewportSize({width,height:900}); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({path:resolve(output,`recovered-history-${width}.png`),fullPage:true}); }
  restored = false; applied = false; saved = null; mode = 'conflict'; await visit(); await page.getByRole('button', { name: '재도전 허용', exact: true }).first().click();
  await page.getByLabel('재도전 사유', { exact: true }).fill('경합 검사'); await page.getByLabel('확인란에 ‘재도전’을 입력해 주세요').fill('재도전'); await page.getByRole('button', { name: '재도전 허용하기' }).click();
  await page.getByRole('button', { name: '목록 다시 확인', exact: true }).click(); await page.getByRole('dialog').waitFor({ state: 'hidden' });
  mode = 'denied'; await page.getByRole('button', { name: '재도전 허용', exact: true }).first().click();
  await page.getByLabel('재도전 사유', { exact: true }).fill('권한 회수 검사'); await page.getByLabel('확인란에 ‘재도전’을 입력해 주세요').fill('재도전'); await page.getByRole('button', { name: '재도전 허용하기' }).click();
  await page.getByRole('heading', { name: 'FAST TAP 지원', exact: true, level: 1 }).waitFor(); await page.waitForFunction(() => !document.querySelector('.tap-support'));
  assert.deepEqual(errors, []); console.log('PASS accepted scores retain signals; recovered records retain past reason/operator/time after reload; separate score/F/rank; five widths; validated/idempotent retry and authorization revocation');
} finally { await browser?.close(); await server.httpServer?.close(); }
