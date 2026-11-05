/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Explicit browser API fixtures. No live submissions, moderation decisions or identities.
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4474, strictPort: true } }); let browser;
try {
  browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const errors = [], routeErrors = [], checks = []; page.on('pageerror', error => errors.push(error.message));
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const blank = () => ({ id: null, version: 0, visibility: null, approvedRevision: null, pendingRevision: null, latestRevision: null, missionCompleted: false, luckScore: null, landingZone: null });
  const slots = { wishes: blank(), cheers: blank() }; let posts = 0, unknown = false, failFeed = false, stale = false, locked = false, lastRequest = null;
  const feeds = { wishes: { items: [], nextCursor: null }, cheers: { items: [], nextCursor: null } };
  function revision(input, number, status = 'QUEUED') { return { id: 100 + number, itemId: 10, number, content: input.content, templateId: input.templateId, style: input.style, anonymous: input.anonymous, status, submittedAt: '2026-01-01T00:00:00Z', reason: null }; }
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), path = new URL(req.url()).pathname.replace('/api/v1', ''), method = req.method();
      const reply = (status, value) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
      if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
      if (method === 'GET' && path === '/users/me') return reply(200, { id: 1, username: 'test-participant', nickname: '테스트', onboardingRequired: false });
      if (method === 'GET' && path === '/tags/pending') return reply(200, { present: false });
      if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'content-test-csrf' });
      if (method === 'GET' && path === '/event') return reply(200, { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
      if (method === 'GET' && path === '/missions/collection') return reply(200, { missions: codes.map((code, index) => ({ id: index + 1, code, state: locked || !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(code) ? 'LOCKED' : slots[code === 'MAKE_A_WISH' ? 'wishes' : 'cheers'].missionCompleted ? 'COMPLETED' : slots[code === 'MAKE_A_WISH' ? 'wishes' : 'cheers'].id ? 'IN_PROGRESS' : 'UNLOCKED', active: true })), completedCount: Number(slots.wishes.missionCompleted) + Number(slots.cheers.missionCompleted), completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } });
      const kind = path.startsWith('/wishes') ? 'wishes' : path.startsWith('/cheers') ? 'cheers' : null;
      if (!kind) throw new Error(`Unexpected ${method} ${path}`);
      if (method === 'GET' && path === '/cheers/likes') return reply(200, { 10: { liked: false, count: 0, canLike: true } });
      if (method === 'GET' && path.endsWith('/me')) return reply(200, slots[kind]);
      if (method === 'GET') return failFeed ? reply(503, { code: 'FEED_UNAVAILABLE', message: '목록을 확인하지 못했어요.' }) : reply(200, feeds[kind]);
      assert.equal(req.headers()['x-xsrf-token'], 'content-test-csrf'); const key = req.headers()['idempotency-key']; assert.match(key, /^[A-Za-z0-9_-]{16,64}$/);
      const body = req.postDataJSON(); assert.deepEqual(Object.keys(body).sort(), ['anonymous', 'content', 'expectedVersion', 'style', 'templateId']); posts++;
      if (lastRequest) { assert.equal(key, lastRequest.key); assert.deepEqual(body, lastRequest.body); }
      if (stale) return reply(409, { code: 'CONTENT_CHANGED', message: '글 상태가 변경됐어요. 다시 불러와 주세요.' });
      if (method === 'PUT') assert.equal(body.expectedVersion, slots[kind].version); else assert.equal(body.expectedVersion, null);
      if (!lastRequest) {
        const r = revision(body, (slots[kind].latestRevision?.number || 0) + 1);
        slots[kind] = { ...slots[kind], id: 10, version: slots[kind].version + 1, visibility: 'VISIBLE', latestRevision: r, pendingRevision: r };
      }
      if (unknown) { unknown = false; lastRequest = { key, body }; return reply(503, { code: 'UNKNOWN_SUBMISSION', message: '제출 응답을 확인하지 못했어요.' }); }
      // Keep the response pending to exercise duplicate-click protection.
      await new Promise(resolve => setTimeout(resolve, 120)); lastRequest = null;
      return reply(202, slots[kind]);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4474${path}`);
  const record = name => { checks.push(name); console.log(`PASS ${name}`); };
  const refresh = () => page.getByRole('button', { name: '내 글과 검수 상태 새로고침', exact: true }).click();
  await visit('/missions/make-a-wish'); await page.getByRole('button', { name: '소원 검수 요청하기', exact: true }).waitFor();
  await page.getByRole('button', { name: '소원 검수 요청하기', exact: true }).click(); await page.getByRole('alert').waitFor(); assert.equal(posts, 0); assert.equal(await page.locator('#content-text').evaluate(el => el === document.activeElement), true);
  assert.equal(await page.locator('.content-feed-list li').count(), 0);
  await page.getByLabel('지금, 어떤 소원을 품고 있나요?', { exact: true }).fill('테스트 전용 소원입니다. 운영에 게시되지 않습니다.');
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({ path: resolve(output, `wish-write-${width}.png`), fullPage: true }); }
  await page.setViewportSize({ width: 390, height: 900 });
  record('empty and invalid wishes do not create a slot; accessible form fits five widths');
  await page.getByRole('button', { name: '소원 검수 요청하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(posts, 1); assert.equal(slots.wishes.missionCompleted, false); assert.equal(await page.locator('.content-feed-list li').count(), 0);
  await page.reload(); await page.getByText('검수 대기', { exact: true }).waitFor(); await page.screenshot({ path: resolve(output, 'wish-pending-390.png'), fullPage: true });
  record('202 and refresh restore a pending revision without publishing it or awarding a letter');
  await visit('/missions/say-something'); await page.getByLabel('어떤 응원을 전하고 싶나요?', { exact: true }).fill('테스트 전용 응원');
  await page.getByRole('radio', { name: '작은 편지', exact: true }).check(); await page.getByRole('radio', { name: '잉크', exact: true }).check(); await page.getByRole('checkbox', { name: '익명으로 남기기', exact: true }).uncheck();
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); for (const h of await page.locator('h1,h2').all()) assert.ok(await h.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1)); await page.screenshot({ path: resolve(output, `cheer-write-${width}.png`), fullPage: true }); }
  await page.setViewportSize({ width: 390, height: 900 }); unknown = true;
  await page.getByRole('button', { name: '응원 검수 요청하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 다시 전송하기', exact: true }).waitFor(); assert.equal(await page.locator('#content-text').isDisabled(), true); await page.getByRole('button', { name: '같은 글 다시 전송하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(posts, 3); assert.equal(slots.cheers.latestRevision.number, 1); assert.equal(slots.cheers.latestRevision.anonymous, false); assert.equal(slots.cheers.latestRevision.templateId, 'NOTE'); assert.equal(slots.cheers.latestRevision.style, 'INK');
  record('cheer presentation and anonymity match the preview; uncertain submission retries identical payload and key');
  const approved = { ...slots.cheers.latestRevision, status: 'APPROVED' }; slots.cheers = { ...slots.cheers, version: 2, approvedRevision: approved, pendingRevision: null, latestRevision: approved, missionCompleted: true };
  feeds.cheers.items = [{ id: 10, content: approved.content, templateId: approved.templateId, style: approved.style, anonymous: false, nickname: '테스트 닉네임', luckScore: null, landingZone: null }];
  await refresh(); await page.locator('.content-status').waitFor(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); const beforeCancel = posts; await page.getByRole('button', { name: '수정 취소하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(posts, beforeCancel); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); await page.locator('#content-text').fill('비공개 검수 중인 테스트 수정본'); await page.getByRole('checkbox', { name: '익명으로 남기기', exact: true }).check(); await page.getByRole('button', { name: '수정본 검수 요청하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor();
  assert.equal(await page.locator('.content-feed-list').getByText('비공개 검수 중인 테스트 수정본', { exact: true }).count(), 0); assert.equal(await page.locator('.content-feed-list').getByText(approved.content, { exact: true }).count(), 1); assert.equal(slots.cheers.missionCompleted, true); await page.screenshot({ path: resolve(output, 'cheer-edit-pending-390.png'), fullPage: true });
  record('editing keeps the old approved body and anonymity public while the new revision stays private');
  stale = true; await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); await page.locator('#content-text').fill('오래된 화면의 수정'); await page.getByRole('button', { name: '수정본 검수 요청하기', exact: true }).click(); await page.getByRole('alert').waitFor(); assert.equal(await page.getByRole('button', { name: '수정본 검수 요청하기', exact: true }).isDisabled(), true); stale = false; await refresh(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor();
  record('a stale revision blocks further writes and requires loading the server state');
  slots.wishes.latestRevision.status = 'REJECTED'; slots.wishes.pendingRevision = null; slots.wishes.version++;
  await visit('/missions/make-a-wish'); await page.getByText('수정이 필요해요', { exact: true }).waitFor(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); await page.locator('#content-text').fill('<script>window.untrusted = true</script>'); await page.getByRole('button', { name: '수정본 검수 요청하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(await page.evaluate(() => window.untrusted), undefined);
  failFeed = true; await refresh(); await page.getByText('목록을 확인하지 못했어요.', { exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).count(), 1); failFeed = false;
  record('rejected text can be corrected in the same slot; text is escaped and list failures retain the private record');
  locked = true; await visit('/missions/make-a-wish'); await page.getByRole('heading', { name: '현장에서 태그를 찾아주세요', exact: true }).waitFor(); assert.equal(await page.locator('#content-text').count(), 0);
  assert.deepEqual(errors, []); assert.deepEqual(routeErrors, []); record('locked missions never expose the writing form');
  await writeFile(resolve(output, 'content-contract-checks.json'), JSON.stringify({ kind: 'explicit browser API fixtures; no live submissions or moderation decisions', checks, errors, routeErrors }, null, 2));
} finally { await browser?.close(); await server.httpServer.close(); }
