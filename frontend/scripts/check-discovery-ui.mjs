/* global document, window, getComputedStyle, requestAnimationFrame */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4476, strictPort: true } }); let browser;
try {
  browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const errors = [], routeErrors = [], checks = []; page.on('pageerror', e => errors.push(e.message));
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const blank = { id: null, version: 0, visibility: null, approvedRevision: null, pendingRevision: null, latestRevision: null, missionCompleted: false, luckScore: null, landingZone: null };
  let signedIn = false, required = false, empty = false, hidden = false, unsafe = false, failOpen = false, discoveryReads = 0, opens = 0, holdCursor = false, releaseCursor = null;
  const visited = new Set(), inbound = new Set([3]);
  const rawPeople = [2, 3, 4].map(id => ({ id, nickname: id === 2 ? '테스트_%!' : id === 3 ? '테스트 달' : '테스트' + '가'.repeat(27), interests: [{ id: 1, name: '백엔드' }, { id: 2, name: '데이터' }], type: id === 2 ? { code: 'I', title: '테스트 취향', version: 'test-v1' } : null, sharedInterests: id === 2 ? ['백엔드'] : [], sameType: id === 2 }));
  const person = id => ({ ...rawPeople.find(p => p.id === id), visited: visited.has(id), visitedMe: inbound.has(id), mutual: visited.has(id) && inbound.has(id) });
  const stats = () => empty ? { visitedCount: 0, visitorCount: 0, mutualCount: 0 } : { visitedCount: visited.size, visitorCount: inbound.size, mutualCount: [...visited].filter(id => inbound.has(id)).length };
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method();
      const reply = (status, value) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
      if (method === 'POST' && path === '/missions/show-me/reconcile') { assert.ok(req.headers()['x-xsrf-token']); return reply(200, { newlyCompleted: false }); }
      if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
      if (method === 'GET' && path === '/users/me') return signedIn ? reply(200, { id: 1, username: 'test-participant', nickname: '테스트 나', onboardingRequired: false }) : reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' });
      if (method === 'GET' && path === '/tags/pending') return reply(200, { present: false });
      if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'discovery-test-csrf' });
      if (method === 'GET' && path === '/event') return reply(200, { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
      if (method === 'GET' && path === '/missions/collection') return reply(200, { missions: codes.map((code, index) => ({ id: index + 1, code, state: ['MAKE_A_WISH', 'SAY_SOMETHING'].includes(code) ? 'UNLOCKED' : 'LOCKED', active: true })), completedCount: 0, completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } });
      if (method === 'GET' && path === '/interests') return reply(200, [{ id: 1, name: '백엔드' }, { id: 2, name: '데이터' }]);
      if (path.startsWith('/discovery/')) {
        discoveryReads++; assert.equal(signedIn, true);
        if (required) return reply(409, { code: 'PROFILE_REQUIRED', message: '내 프로필과 현재 공개 동의를 먼저 확인해 주세요.' });
        if (method === 'GET' && path === '/discovery/stats') return reply(200, stats());
        if (method === 'GET' && path === '/discovery/recommendations') return reply(200, empty ? [] : [person(4), person(3), person(2)]);
        if (method === 'GET' && path === '/discovery/people') {
          if (empty) return reply(200, { items: [], nextCursor: null });
          const cursor = url.searchParams.get('cursor'), q = url.searchParams.get('q');
          if (cursor && holdCursor) { holdCursor = false; await new Promise(r => { releaseCursor = r; }); return reply(200, { items: [person(2)], nextCursor: null }); }
          if (q === '테스트_%!') { assert.equal(url.searchParams.get('interestId'), '1'); return reply(200, { items: [person(2)], nextCursor: null }); }
          if (q === '테스트') return reply(200, cursor ? { items: [person(3)], nextCursor: null } : { items: [person(4)], nextCursor: 4 });
          return reply(200, cursor ? { items: [person(2)], nextCursor: null } : { items: [person(4), person(3)], nextCursor: 3 });
        }
        if (method === 'GET' && path === '/discovery/graph') { assert.equal(url.searchParams.get('limit'), '30'); const nodes = empty ? [] : rawPeople.filter(p => visited.has(p.id) || inbound.has(p.id)).map(p => person(p.id)); return reply(200, { self: { id: 1, nickname: '테스트 나' }, nodes, edges: nodes.flatMap(p => [...(p.visited ? [{ from: 1, to: p.id, weight: p.mutual ? 6 : 1 }] : []), ...(p.visitedMe ? [{ from: p.id, to: 1, weight: p.mutual ? 6 : 1 }] : [])]), truncated: false }); }
        if (method === 'GET' && path === '/discovery/profiles/2/photo') { assert.equal(visited.has(2), true); return route.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aENcAAAAASUVORK5CYII=', 'base64') }); }
        const match = path.match(/^\/discovery\/profiles\/(\d+)\/open$/);
        if (match && method === 'POST') {
          assert.equal(req.headers()['x-xsrf-token'], 'discovery-test-csrf'); assert.equal(req.postData(), null); opens++;
          if (hidden) return reply(404, { code: 'PROFILE_NOT_FOUND', message: '공개된 프로필을 찾을 수 없어요.' });
          const id = Number(match[1]), first = !visited.has(id); visited.add(id);
          if (failOpen) { failOpen = false; return reply(503, { message: '프로필 응답을 확인하지 못했어요.' }); }
          return reply(200, { person: person(id), realName: `테스트 실명${id}`, region: '서울', classNo: 3, bio: '<script>window.profileInjected = true</script>\n테스트용 상세 소개', githubUrl: unsafe ? 'javascript:alert(1)' : 'https://github.com/test-only', blogUrl: unsafe ? 'https://name:secret@example.com' : 'https://example.com/test', photoUrl: '/api/v1/discovery/profiles/2/photo?v=9', newlyVisited: first });
        }
      }
      if (method === 'GET' && ['/wishes/me', '/cheers/me'].includes(path)) return reply(200, blank);
      if (method === 'GET' && path === '/wishes') return reply(200, { items: [{ id: 71, content: '테스트 소원', templateId: 'PLAIN', style: 'ROSE', anonymous: true, nickname: null, profileId: null, luckScore: null, landingZone: null }], nextCursor: null });
      if (method === 'GET' && path === '/wishes/71/comments/me') return reply(200, { items: [], nextCursor: null });
      if (method === 'GET' && path === '/wishes/71/comments') return reply(200, { items: [{ id: 701, content: '공개 설정 댓글', revealProfile: true, nickname: '테스트 공개', profileId: 2 }, { id: 702, content: '익명 댓글', revealProfile: false, nickname: null, profileId: 999 }], nextCursor: null });
      if (method === 'GET' && path === '/cheers') return reply(200, { items: [{ id: 81, content: '공개 응원', templateId: 'NOTE', style: 'INK', anonymous: false, nickname: '테스트 공개', profileId: 2 }, { id: 82, content: '익명 응원', templateId: 'PLAIN', style: 'ROSE', anonymous: true, nickname: null, profileId: 999 }], nextCursor: null });
      if (method === 'GET' && path === '/cheers/likes') return reply(200, { 81: { liked: false, count: 0, canLike: true }, 82: { liked: false, count: 0, canLike: false } });
      throw new Error(`Unexpected ${method} ${path}`);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4476${path}`); const record = text => { checks.push(text); console.log(`PASS ${text}`); };
  await visit('/discovery'); await page.getByRole('heading', { name: '함께할 동료를 만나봐요', exact: true }).waitFor(); assert.equal(discoveryReads, 0); assert.equal(opens, 0);
  const guestHeadings=new Map();
  const headingMetrics=()=>page.locator('.page-heading > *').evaluateAll(elements=>elements.map(el=>{const r=el.getBoundingClientRect(),s=getComputedStyle(el);return {text:el.textContent,x:r.x,y:r.y,font:s.fontSize,line:s.lineHeight};}));
  for(const width of [360,390,430,768,1440]) {
    await page.setViewportSize({width,height:900});
    assert.equal(await page.locator('.page.discovery-page.participant-page').count(),1);
    assert.deepEqual(await page.locator('.page-heading > *').allTextContents(),['TAG ME 네트워크','동료 찾기','같은 관심사에서 대화를 시작해요.']);
    guestHeadings.set(width,await headingMetrics());
    await page.screenshot({path:resolve(output,`discovery-guest-${width}.png`),fullPage:true});
  }
  assert.equal(discoveryReads,0);assert.equal(opens,0);
  record('anonymous navigation shows login without fetching discovery data or opening a profile');
  signedIn = true; await page.reload(); await page.locator('.person-row').first().waitFor(); assert.equal(await page.getByText('테스트 실명2', { exact: true }).count(), 0); assert.equal(opens, 0); assert.equal(await page.locator('img').count(), 0);
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.deepEqual(await headingMetrics(),guestHeadings.get(width),'guest and participant share the same heading layout'); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); for (const h of await page.locator('h1,h2').all()) assert.ok(await h.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1)); await page.screenshot({ path: resolve(output, `discovery-${width}.png`), fullPage: true }); }
  await page.setViewportSize({ width: 390, height: 900 }); record('recommendations show only public nicknames, real reasons and server counts at five widths');
  await page.getByRole('link', { name: '전체 동료', exact: true }).click(); await page.getByLabel('닉네임 검색', { exact: true }).fill('테스트_%!'); await page.getByLabel('관심사', { exact: true }).selectOption('1'); await page.getByRole('button', { name: '동료 검색하기', exact: true }).click(); await page.getByRole('link', { name: '테스트_%! 프로필 보기', exact: true }).waitFor(); assert.equal(await page.locator('.person-row').count(), 1);
  await page.goBack(); await page.getByRole('button', { name: '동료 더 보기', exact: true }).waitFor(); assert.equal(await page.getByLabel('닉네임 검색', { exact: true }).inputValue(), '');
  holdCursor = true; await page.getByRole('button', { name: '동료 더 보기', exact: true }).click(); await page.getByLabel('닉네임 검색', { exact: true }).fill('테스트'); await page.getByRole('button', { name: '동료 검색하기', exact: true }).click(); await page.getByRole('button', { name: '동료 더 보기', exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '동료 더 보기', exact: true }).isDisabled(), false); assert.ok(releaseCursor); releaseCursor();
  await page.getByRole('button', { name: '동료 더 보기', exact: true }).click(); await page.getByRole('link', { name: '테스트 달 프로필 보기', exact: true }).waitFor(); assert.equal(await page.getByRole('link', { name: '테스트_%! 프로필 보기', exact: true }).count(), 0); assert.equal(opens, 0);
  record('nickname and interest filters survive back navigation; stale pagination never overwrites a new search');
  await visit('/discovery'); await page.getByRole('link', { name: '테스트_%! 프로필 보기', exact: true }).click(); await page.getByText('테스트 실명2', { exact: true }).waitFor(); assert.equal(opens, 1); assert.equal(await page.getByText('처음 열어본 프로필이에요', { exact: true }).count(), 1); assert.equal(visited.size, 1); assert.equal(await page.evaluate(() => window.profileInjected), undefined); assert.equal(await page.getByRole('link', { name: 'GitHub 열기 (새 탭)', exact: true }).getAttribute('rel'), 'noopener noreferrer'); assert.equal((await page.title()).includes('실명'), false);
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({ path: resolve(output, `public-profile-${width}.png`), fullPage: true }); }
  await page.reload(); await page.getByText('이전에 열어본 프로필이에요', { exact: true }).waitFor(); assert.equal(visited.size, 1); await page.setViewportSize({ width: 390, height: 900 });
  record('explicit profile navigation uses CSRF POST, renders detail safely and preserves one natural visit on refresh');
  failOpen = true; await page.reload(); await page.getByRole('button', { name: '프로필 다시 열기', exact: true }).waitFor(); assert.equal(await page.locator('.public-profile').count(), 0); await page.getByRole('button', { name: '프로필 다시 열기', exact: true }).click(); await page.getByText('테스트 실명2', { exact: true }).waitFor(); assert.equal(visited.size, 1);
  hidden = true; await page.reload(); await page.getByText('공개된 프로필을 찾을 수 없어요.', { exact: true }).waitFor(); assert.equal(await page.getByText('테스트 실명2', { exact: true }).count(), 0); assert.equal(await page.locator('.public-profile').count(), 0); hidden = false; unsafe = true; await page.reload(); await page.getByText('테스트 실명2', { exact: true }).waitFor(); assert.equal(await page.locator('.public-profile-links a').count(), 0); unsafe = false;
  record('uncertain detail retries are idempotent; private profiles clear old fields and unsafe external links stay hidden');
  inbound.add(2); await visit('/discovery/graph'); await page.locator('.discovery-graph').waitFor(); assert.equal(await page.locator('.discovery-graph line').count(), 2); assert.equal(await page.getByText('서로 열어봤어요', { exact: true }).count(), 1);
  for (const width of [360, 390, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({ path: resolve(output, `discovery-graph-${width}.png`), fullPage: true }); }
  empty = true; await page.reload(); await page.getByText('아직 공개된 열람 관계가 없어요.', { exact: true }).waitFor(); assert.equal(await page.locator('.visit-stats dd').first().textContent(), '0명'); empty = false;
  empty = false; await visit('/discovery/graph'); await page.locator('.network-node').first().waitFor();
  assert.equal(await page.locator('.network-strong line').count(), 1);
  assert.equal(await page.locator('.network-strong line').first().evaluate(el => getComputedStyle(el).strokeWidth), '4px');
  const opensBeforeNode = opens; await page.locator('.network-node').first().click();
  await page.locator('.public-profile').waitFor(); assert.equal(opens, opensBeforeNode + 1);
  const readsBeforeHome = discoveryReads; await visit('/'); await page.locator('.mission-section').waitFor();
  assert.equal(await page.locator('.discovery-graph, .home-network').count(), 0); assert.equal(discoveryReads, readsBeforeHome);
  record('direct relationship arrows and accessible rows use server data; empty graphs keep real zero counts');
  // A response page is larger than the diagram; the next page stays reachable.
  const connected = Array.from({ length: 31 }, (_, i) => ({
    ...person(4), id: i + 10, nickname: '긴닉네임'.repeat(7) + i, visited: true, visitedMe: false, mutual: false,
  }));
  await page.route('**/api/v1/discovery/graph?**', route => {
    const start = Number(new URL(route.request().url()).searchParams.get('offset'));
    const nodes = connected.slice(start, start + 30);
    return route.fulfill({ json: { self: { id: 1, nickname: '나' }, nodes,
      edges: nodes.map(p => ({ from: 1, to: p.id, weight: 1 })), truncated: start + nodes.length < connected.length } });
  });
  await visit('/discovery/graph'); await page.locator('.network-node').first().waitFor();
  for (const width of [360, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    const boxes = await Promise.all((await page.locator('.network-node').all()).map(n => n.boundingBox()));
    assert.ok(boxes.length > 0 && boxes.length < 30);
    for (const [i, a] of boxes.entries()) for (const b of boxes.slice(i+1)) {
      assert.ok(a.x+a.width <= b.x || b.x+b.width <= a.x || a.y+a.height <= b.y || b.y+b.height <= a.y);
    }
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false);
  }
  assert.equal(await page.locator('.people-list li').count(), 30);
  await page.getByRole('button', { name: '연결된 동료 더 보기' }).click();
  await page.locator('.people-list li').nth(30).waitFor();
  assert.equal(await page.getByRole('button', { name: '연결된 동료 더 보기' }).count(), 0);
  record('30 long nicknames fit separated graph targets at five widths; the list loads the 31st relationship');
  required = true; await visit('/discovery'); await page.getByRole('link', { name: '내 프로필과 참여 안내 확인하기', exact: true }).waitFor(); assert.equal(await page.locator('.person-row').count(), 0); assert.equal(await page.locator('.visit-stats').count(), 0); required = false;
  await visit('/missions/say-something'); await page.getByRole('link', { name: '테스트 공개의 프로필 보기', exact: true }).waitFor(); assert.equal(await page.locator('.content-feed-list a[href="/discovery/profiles/999"]').count(), 0); assert.equal(await page.locator('.content-feed-list a[href="/discovery/profiles/2"]').count(), 1);
  await visit('/missions/make-a-wish'); await page.getByRole('button', { name: '소원 1 열기', exact: true }).click(); await page.getByRole('button', { name: '댓글 보기', exact: true }).click(); await page.getByRole('link', { name: '테스트 공개의 프로필 보기', exact: true }).waitFor(); assert.equal(await page.locator('.comment-list a[href="/discovery/profiles/999"]').count(), 0); await page.getByRole('link', { name: '테스트 공개의 프로필 보기', exact: true }).click(); await page.getByText('테스트 실명2', { exact: true }).waitFor();
  record('current profile consent gates discovery and only explicitly public content links to the supplied profile ID');
  assert.deepEqual(errors, []); assert.deepEqual(routeErrors, []); await writeFile(resolve(output, 'discovery-contract-checks.json'), JSON.stringify({ kind: 'explicit API fixtures; no live profiles or visit records', checks, errors, routeErrors }, null, 2));
} finally { await browser?.close(); await server.httpServer.close(); }
