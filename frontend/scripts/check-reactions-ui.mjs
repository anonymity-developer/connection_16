/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4475, strictPort: true } }); let browser;
try {
  browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const errors = [], routeErrors = [], checks = []; page.on('pageerror', error => errors.push(error.message));
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const blank = { id: null, version: 0, visibility: null, approvedRevision: null, pendingRevision: null, latestRevision: null, missionCompleted: false, luckScore: null, landingZone: null };
  const wish = { id: 71, content: '테스트용 소원입니다. 실제로 공개된 참여자 데이터가 아닙니다.', templateId: 'PLAIN', style: 'ROSE', anonymous: true, nickname: null, luckScore: null, landingZone: null };
  const cards = [81, 82].map(id => ({ ...wish, id, content: id === 81 ? '서로의 내일을 응원하는 테스트 카드' : '하트를 남길 수 없는 카드의 테스트 상태', templateId: 'NOTE', style: 'INK' }));
  let mine = [], comments = [], posts = 0, failComment = false, commentRequest = null, publicHidden = false, commentReads = 0, puts = 0, deletes = 0, failPut = false, failDelete = false, failBatch = false, forbidden = false, batches = 0, singles = 0;
  const hearts = { 81: { liked: false, count: 0, canLike: true }, 82: { liked: false, count: 0, canLike: false } };
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method();
      const reply = (status, value) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
      if (method === 'POST' && path === '/missions/show-me/reconcile') { assert.ok(req.headers()['x-xsrf-token']); return reply(200, { newlyCompleted: false }); }
      if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
      if (method === 'GET' && path === '/discovery/graph') return reply(200, { self: { id: 1, nickname: '테스트' }, nodes: [], edges: [], truncated: false });
      if (method === 'GET' && path === '/users/me') return reply(200, { id: 1, username: 'test-participant', nickname: '테스트', onboardingRequired: false });
      if (method === 'GET' && path === '/tags/pending') return reply(200, { present: false });
      if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'reaction-test-csrf' });
      if (method === 'GET' && path === '/event') return reply(200, { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
      if (method === 'GET' && path === '/missions/collection') return reply(200, { missions: codes.map((code, index) => ({ id: index + 1, code, state: ['MAKE_A_WISH', 'SAY_SOMETHING'].includes(code) ? 'UNLOCKED' : 'LOCKED', active: true })), completedCount: 0, completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } });
      if (method === 'GET' && ['/wishes/me', '/cheers/me'].includes(path)) return reply(200, blank);
      if (method === 'GET' && path === '/wishes') return reply(200, { items: [wish], nextCursor: null });
      if (method === 'GET' && path === '/cheers') return reply(200, { items: cards, nextCursor: null });
      if (method === 'GET' && path === '/wishes/71/comments/me') { commentReads++; return reply(200, { items: mine, nextCursor: null }); }
      if (method === 'GET' && path === '/wishes/71/comments') { commentReads++; return publicHidden ? reply(404, { code: 'CONTENT_NOT_FOUND', message: '공개된 글을 찾을 수 없어요.' }) : reply(200, { items: comments, nextCursor: null }); }
      if (method === 'GET' && path === '/cheers/likes') { batches++; assert.deepEqual(url.searchParams.get('ids').split(',').map(Number), [81, 82]); return failBatch ? reply(503, { message: '하트 묶음 조회에 실패했어요.' }) : reply(200, hearts); }
      if (method === 'GET' && /^\/cheers\/\d+\/like$/.test(path)) { singles++; return reply(200, hearts[Number(path.split('/')[2])]); }
      assert.equal(req.headers()['x-xsrf-token'], 'reaction-test-csrf');
      if (method === 'POST' && path === '/wishes/71/comments') {
        posts++; const body = req.postDataJSON(), key = req.headers()['idempotency-key']; assert.deepEqual(Object.keys(body).sort(), ['content', 'revealProfile']); assert.match(key, /^[A-Za-z0-9_-]{16,64}$/);
        if (commentRequest) { assert.equal(key, commentRequest.key); assert.deepEqual(body, commentRequest.body); }
        let saved = mine.find(item => item.requestKey === key); if (!saved) { saved = { id: posts, content: body.content, revealProfile: body.revealProfile, status: 'QUEUED', reason: null, submittedAt: '2026-01-01T00:00:00Z', requestKey: key }; mine = [saved, ...mine]; }
        if (failComment) { failComment = false; commentRequest = { key, body }; return reply(503, { message: '댓글 응답을 확인하지 못했어요.' }); }
        commentRequest = null; return reply(202, saved);
      }
      if (path === '/cheers/81/like') {
        if (forbidden) return reply(403, { code: 'EVENT_CLOSED', message: '지금은 하트를 변경할 수 없어요.' });
        if (method === 'PUT') { puts++; hearts[81] = { liked: true, count: 1, canLike: true }; if (failPut) { failPut = false; return reply(503, { message: '하트 응답을 확인하지 못했어요.' }); } return reply(200, hearts[81]); }
        if (method === 'DELETE') { deletes++; hearts[81] = { liked: false, count: 0, canLike: true }; if (failDelete) { failDelete = false; return reply(503, { message: '취소 응답을 확인하지 못했어요.' }); } return route.fulfill({ status: 204 }); }
      }
      throw new Error(`Unexpected ${method} ${path}`);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4475${path}`); const record = text => { checks.push(text); console.log(`PASS ${text}`); };
  await visit('/missions/make-a-wish'); const feed = page.locator('.content-feed'); await feed.getByRole('button', { name: '소원 1 열기', exact: true }).click(); await feed.getByRole('button', { name: '댓글 보기', exact: true }).waitFor(); assert.equal(commentReads, 0); await feed.getByRole('button', { name: '댓글 보기', exact: true }).click(); const thread = page.locator('.comment-thread');
  await thread.getByRole('button', { name: '댓글 남기기', exact: true }).waitFor(); await thread.getByRole('button', { name: '댓글 남기기', exact: true }).click(); await thread.getByRole('alert').waitFor(); assert.equal(posts, 0); assert.equal(await thread.locator('textarea').evaluate(el => el === document.activeElement), true);
  record('comments load only when opened and blank input focuses the field without submitting');
  await thread.getByLabel('동료의 소원에 댓글 남기기', { exact: true }).fill('테스트 댓글입니다.'); failComment = true;
  await thread.getByRole('button', { name: '댓글 남기기', exact: true }).click(); await thread.getByRole('button', { name: '같은 댓글 다시 보내기', exact: true }).waitFor(); assert.equal(await thread.locator('textarea').isDisabled(), true); await thread.getByRole('button', { name: '같은 댓글 다시 보내기', exact: true }).click(); await thread.locator('.own-comments .comment-list li').first().waitFor(); assert.equal(posts, 2); assert.equal(mine.length, 1); assert.equal(await thread.locator(':scope > .comment-list li').count(), 0);
  await page.reload(); await feed.getByRole('button', { name: '소원 1 열기', exact: true }).click(); await feed.getByRole('button', { name: '댓글 보기', exact: true }).click(); await page.locator('.own-comments .comment-list li').first().waitFor(); assert.equal(await page.locator('.content-status').getByText('글자 획득 완료', { exact: true }).count(), 0);
  record('uncertain comments retry the same key and text; pending state restores without public text or a letter');
  const current = page.locator('.comment-thread'); await current.locator('textarea').fill('다른 테스트 댓글'); await current.getByRole('checkbox', { name: '닉네임 표시하기', exact: true }).check(); failComment = true; await current.getByRole('button', { name: '댓글 남기기', exact: true }).click(); await current.getByRole('button', { name: '같은 댓글 다시 보내기', exact: true }).waitFor(); const beforeRefresh = posts;
  await current.getByRole('button', { name: '댓글 상태 새로고침', exact: true }).click(); await page.waitForFunction(() => document.querySelector('.comment-thread textarea')?.disabled === false); assert.equal(await current.locator('textarea').inputValue(), ''); assert.equal(posts, beforeRefresh); assert.equal(mine.length, 2); assert.equal(mine[0].revealProfile, true); commentRequest = null;
  record('private request keys recover a saved uncertain comment on refresh without creating another comment');
  mine = mine.map(item => ({ ...item, status: 'APPROVED' })); comments = mine.map(item => ({ id: item.id, content: item.id === 1 ? '<script>window.reactionInjected = true</script>' : item.content, revealProfile: item.revealProfile, nickname: item.revealProfile ? '테스트 닉네임' : null, profileId: item.revealProfile ? 1 : null }));
  await current.getByRole('button', { name: '댓글 상태 새로고침', exact: true }).click(); await current.locator(':scope > .comment-list li').first().waitFor(); assert.equal(await page.evaluate(() => window.reactionInjected), undefined); assert.equal(await current.locator(':scope > .comment-list').getByRole('link', { name: '테스트 닉네임의 프로필 보기', exact: true }).count(), 1); assert.equal(await current.locator(':scope > .comment-list a').count(), 1);
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); for (const h of await page.locator('h1,h2').all()) assert.ok(await h.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1)); await current.screenshot({ path: resolve(output, `comments-${width}.png`) }); }
  record('approved comments render escaped text and explicit nickname visibility at five widths');
  publicHidden = true; await current.getByRole('button', { name: '댓글 상태 새로고침', exact: true }).click(); await current.getByText('공개된 글을 찾을 수 없어요.', { exact: true }).waitFor(); assert.equal(await current.locator('form').count(), 0); assert.equal(await current.locator('.own-comments .comment-list li').count(), 2); publicHidden = false;
  record('a nonpublic parent removes the public thread and writing form while retaining only private own history');
  await visit('/missions/say-something'); const controls = page.locator('.heart-control'); await controls.first().getByText('하트 0개', { exact: true }).waitFor(); assert.equal(batches, 1); assert.equal(singles, 0); assert.equal(await controls.nth(1).getByRole('button', { name: '하트 남기기', exact: true }).isDisabled(), true);
  failPut = true; await controls.first().getByRole('button', { name: '하트 남기기', exact: true }).click(); await controls.first().getByRole('button', { name: '같은 하트 요청 다시 보내기', exact: true }).waitFor(); assert.equal(await controls.first().getByText('하트 0개', { exact: true }).count(), 1); await controls.first().getByRole('button', { name: '같은 하트 요청 다시 보내기', exact: true }).click(); await controls.first().getByText('하트 1개', { exact: true }).waitFor(); assert.equal(puts, 2);
  failDelete = true; await controls.first().getByRole('button', { name: '하트 취소하기', exact: true }).click(); await controls.first().getByRole('button', { name: '같은 하트 요청 다시 보내기', exact: true }).waitFor(); assert.equal(await controls.first().getByText('하트 1개', { exact: true }).count(), 1); await controls.first().getByRole('button', { name: '같은 하트 요청 다시 보내기', exact: true }).click(); await controls.first().getByText('하트 0개', { exact: true }).waitFor(); assert.equal(deletes, 2);
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.locator('.content-feed').screenshot({ path: resolve(output, `hearts-${width}.png`) }); }
  record('hearts load once per page, disallow ineligible cards and retry PUT/DELETE without optimistic count changes');
  forbidden = true; await controls.first().getByRole('button', { name: '하트 남기기', exact: true }).click(); await controls.first().getByRole('alert').waitFor(); assert.equal(await controls.first().getByRole('button', { name: '하트 남기기', exact: true }).isDisabled(), true); hearts[81].canLike = false; await controls.first().getByRole('button', { name: '서버의 하트 상태 확인하기', exact: true }).click(); await controls.first().getByText('지금은 이 카드에 하트를 남기거나 취소할 수 없어요.', { exact: true }).waitFor();
  failBatch = true; await page.reload(); await page.getByText('하트 묶음 조회에 실패했어요.', { exact: true }).waitFor(); assert.equal(await page.locator('.heart-count').count(), 0); await page.locator('.heart-control').first().getByRole('button', { name: '하트 상태 확인하기', exact: true }).click(); await page.locator('.heart-control').first().getByText('하트 0개', { exact: true }).waitFor();
  record('forbidden mutations require server reconciliation and failed batch reads never invent a zero count');
  assert.deepEqual(errors, []); assert.deepEqual(routeErrors, []); await writeFile(resolve(output, 'reactions-contract-checks.json'), JSON.stringify({ kind: 'explicit API fixtures; no live comments, hearts or moderation decisions', checks, errors, routeErrors }, null, 2));
} finally { await browser?.close(); await server.httpServer.close(); }
