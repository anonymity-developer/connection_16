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
  const errors = [], routeErrors = [], checks = []; await page.addLocatorHandler(page.locator('.mission-earned-actions'), async () => { await page.getByRole('button', {name:'계속 참여하기',exact:true}).click(); });
  page.on('pageerror', error => errors.push(error.message));
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const blank = () => ({ id: null, version: 0, visibility: null, approvedRevision: null, pendingRevision: null, latestRevision: null, missionCompleted: false, luckScore: null, landingZone: null });
  const slots = { wishes: blank(), cheers: blank() }; let rejectWish = false; let posts = 0, unknown = false, failFeed = false, stale = false, locked = false, lastRequest = null;
  const feeds = { wishes: { items: [], nextCursor: null }, cheers: { items: [], nextCursor: null } };
  let olderWishes = [], contentReads = 0; const contentRequests = [];
  function revision(input, number, status = 'QUEUED') { return { id: 100 + number, itemId: 10, number, content: input.content, templateId: input.templateId, style: input.style, anonymous: input.anonymous, status, submittedAt: '2026-01-01T00:00:00Z', reason: null }; }
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), path = new URL(req.url()).pathname.replace('/api/v1', ''), method = req.method();
      const reply = (status, value) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
      if (method === 'POST' && path === '/missions/show-me/reconcile') { assert.ok(req.headers()['x-xsrf-token']); return reply(200, { newlyCompleted: false }); }
      if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
      if (method === 'GET' && path === '/discovery/graph') return reply(200, { self: { id: 1, nickname: '테스트' }, nodes: [], edges: [], truncated: false });
      if (method === 'GET' && path === '/users/me') return reply(200, { id: 1, username: 'test-participant', nickname: '테스트', onboardingRequired: false });
      if (method === 'GET' && path === '/tags/pending') return reply(200, { present: false });
      if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'content-test-csrf' });
      if (method === 'GET' && path === '/event') return reply(200, { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
      if (method === 'GET' && path === '/missions/collection') return reply(200, { missions: codes.map((code, index) => ({ id: index + 1, code, state: locked || !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(code) ? 'LOCKED' : slots[code === 'MAKE_A_WISH' ? 'wishes' : 'cheers'].missionCompleted ? 'COMPLETED' : slots[code === 'MAKE_A_WISH' ? 'wishes' : 'cheers'].id ? 'IN_PROGRESS' : 'UNLOCKED', active: true })), completedCount: Number(slots.wishes.missionCompleted) + Number(slots.cheers.missionCompleted), completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } });
      if (method === 'POST' && path === '/wishes' && rejectWish) return reply(400, {code:'INVALID_CONTENT',message:'테스트 전용 제출 실패'});
      const kind = path.startsWith('/wishes') ? 'wishes' : path.startsWith('/cheers') ? 'cheers' : null;
      if (!kind) throw new Error(`Unexpected ${method} ${path}`);
      if (method === 'GET') { contentReads++; contentRequests.push({path,locked,page:page.url(),referrer:req.headers()['referer']}); }
      if (method === 'GET' && path === '/cheers/likes') return reply(200, { 10: { liked: false, count: 0, canLike: true } });
      if (method === 'GET' && /^\/wishes\/\d+\/comments(?:\/me)?$/.test(path)) return reply(200, { items: [], nextCursor: null });
      if (method === 'GET' && path.endsWith('/me')) return reply(200, slots[kind]);
      if (method === 'GET') return failFeed ? reply(503, { code: 'FEED_UNAVAILABLE', message: '목록을 확인하지 못했어요.' }) : reply(200, kind === 'wishes' && new URL(req.url()).searchParams.has('cursor') ? { items: olderWishes, nextCursor: null } : feeds[kind]);
      assert.equal(req.headers()['x-xsrf-token'], 'content-test-csrf'); const key = req.headers()['idempotency-key']; assert.match(key, /^[A-Za-z0-9_-]{16,64}$/);
      const body = req.postDataJSON(); assert.deepEqual(Object.keys(body).sort(), ['anonymous', 'content', 'expectedVersion', 'style', 'templateId']); posts++;
      if (lastRequest) { assert.equal(key, lastRequest.key); assert.deepEqual(body, lastRequest.body); }
      if (stale) return reply(409, { code: 'CONTENT_CHANGED', message: '글 상태가 변경됐어요. 다시 불러와 주세요.' });
      if (method === 'PUT') assert.equal(body.expectedVersion, slots[kind].version); else assert.equal(body.expectedVersion, null);
      if (!lastRequest) {
        const r = revision(body, (slots[kind].latestRevision?.number || 0) + 1);
        slots[kind] = { ...slots[kind], id: 10, version: slots[kind].version + 1, visibility: 'VISIBLE', latestRevision: r, pendingRevision: r, missionCompleted: true };
      }
      if (unknown) { unknown = false; lastRequest = { key, body }; return reply(503, { code: 'UNKNOWN_SUBMISSION', message: '제출 응답을 확인하지 못했어요.' }); }
      // Keep the response pending to exercise duplicate-click protection.
      await new Promise(resolve => setTimeout(resolve, 120)); lastRequest = null;
      return reply(202, slots[kind]);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4474${path}`);
  const record = name => { checks.push(name); console.log(`PASS ${name}`); };
  const refresh = () => page.getByRole('button', { name: '내 글과 접수 상태 새로고침', exact: true }).click();
  await visit('/missions/make-a-wish'); await page.getByRole('button', { name: '동전 던지기', exact: true }).waitFor();
  await page.getByRole('button', { name: '동전 던지기', exact: true }).click(); await page.getByRole('alert').waitFor(); assert.equal(posts, 0); assert.equal(await page.locator('#content-text').evaluate(el => el === document.activeElement), true);
  assert.equal(await page.locator('.content-feed-list li').count(), 0);
  await page.getByLabel('지금, 어떤 소원을 품고 있나요?', { exact: true }).fill('테스트 전용 소원입니다. 운영에 게시되지 않습니다.');
  for (const width of [360, 375, 390, 412, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({ path: resolve(output, `wish-write-${width}.png`), fullPage: true }); }
  await page.setViewportSize({ width: 390, height: 900 });
  rejectWish = true; await page.getByRole('button', {name:'동전 던지기',exact:true}).click(); await page.getByRole('alert').waitFor(); assert.equal(await page.locator('.wish-toss').count(),0); rejectWish = false;
  record('empty and invalid wishes do not create a slot; accessible form fits five widths');
  await page.getByRole('button', { name: '동전 던지기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(posts, 1); assert.equal(await page.locator('.wish-toss').count(),1); await page.emulateMedia({reducedMotion:'reduce'}); assert.equal(await page.locator('.toss-coin').evaluate(el => getComputedStyle(el).animationName),'none'); await page.emulateMedia({reducedMotion:'no-preference'}); assert.equal(slots.wishes.missionCompleted, true); assert.equal(await page.locator('.content-feed-list li').count(), 0);
  await page.getByText('A를 획득했어요!', { exact: true }).waitFor(); await page.reload(); await page.locator('.content-review-state').getByText('내가 남긴 글', { exact: true }).waitFor(); assert.equal(await page.locator('.wish-toss').count(),0); await page.screenshot({ path: resolve(output, 'wish-pending-390.png'), fullPage: true });
  record('202 grants A while the pending revision stays private across reloads');
  await visit('/missions/say-something'); await page.getByLabel('어떤 응원을 전하고 싶나요?', { exact: true }).fill('테스트 전용 응원');
  await page.getByRole('radio', { name: '작은 편지', exact: true }).check();
  const cheerPreview = page.getByLabel('응원 카드 미리보기', { exact: true });
  assert.equal(await cheerPreview.evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(255, 240, 244)');
  assert.match(await cheerPreview.evaluate(el => getComputedStyle(el, '::after').backgroundImage), /data:image\/svg/);
  await page.getByRole('radio', { name: '하늘', exact: true }).check();
  assert.equal(await cheerPreview.evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(232, 247, 255)');
  await page.getByRole('checkbox', { name: '익명으로 남기기', exact: true }).uncheck();
  for (const width of [360, 375, 390, 412, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); for (const h of await page.locator('h1,h2').all()) assert.ok(await h.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1)); await page.screenshot({ path: resolve(output, `cheer-write-${width}.png`), fullPage: true }); }
  await page.setViewportSize({ width: 390, height: 900 }); unknown = true;
  await page.getByRole('button', { name: '응원 보내기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 다시 전송하기', exact: true }).waitFor(); assert.equal(await page.locator('#content-text').isDisabled(), true); await page.getByRole('button', { name: '같은 글 다시 전송하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(posts, 3); assert.equal(slots.cheers.latestRevision.number, 1); assert.equal(slots.cheers.latestRevision.anonymous, false); assert.equal(slots.cheers.latestRevision.templateId, 'NOTE'); assert.equal(slots.cheers.latestRevision.style, 'INK'); await page.getByText('Y를 획득했어요!', { exact: true }).waitFor();
  record('cheer presentation and anonymity match the preview; uncertain submission retries identical payload and key');
  const approved = { ...slots.cheers.latestRevision, status: 'APPROVED' }; slots.cheers = { ...slots.cheers, version: 2, approvedRevision: approved, pendingRevision: null, latestRevision: approved, missionCompleted: true };
  feeds.cheers.items = [{ id: 10, content: approved.content, templateId: approved.templateId, style: approved.style, anonymous: false, nickname: '테스트 닉네임', luckScore: null, landingZone: null }];
  await refresh(); await page.locator('.content-status').waitFor(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); const beforeCancel = posts; await page.getByRole('button', { name: '수정 취소하기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(posts, beforeCancel); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); await page.locator('#content-text').fill('비공개 검수 중인 테스트 수정본'); await page.getByRole('checkbox', { name: '익명으로 남기기', exact: true }).check(); await page.getByRole('button', { name: '수정 내용 보내기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor();
  assert.equal(await page.locator('.content-feed-list').getByText('비공개 검수 중인 테스트 수정본', { exact: true }).count(), 0); assert.equal(await page.locator('.content-feed-list').getByText(approved.content, { exact: true }).count(), 1); assert.equal(slots.cheers.missionCompleted, true); await page.screenshot({ path: resolve(output, 'cheer-edit-pending-390.png'), fullPage: true });
  record('editing keeps the old approved body and anonymity public while the new revision stays private');
  stale = true; await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); await page.locator('#content-text').fill('오래된 화면의 수정'); await page.getByRole('button', { name: '수정 내용 보내기', exact: true }).click(); await page.getByRole('alert').waitFor(); assert.equal(await page.getByRole('button', { name: '수정 내용 보내기', exact: true }).isDisabled(), true); stale = false; await refresh(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor();
  record('a stale revision blocks further writes and requires loading the server state');
  slots.wishes.latestRevision.status = 'REJECTED'; slots.wishes.pendingRevision = null; slots.wishes.version++;
  await visit('/missions/make-a-wish'); await page.getByText('수정이 필요해요', { exact: true }).waitFor(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).click(); await page.locator('#content-text').fill('<script>window.untrusted = true</script>'); await page.getByRole('button', { name: '수정 내용 보내기', exact: true }).click(); await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).waitFor(); assert.equal(await page.evaluate(() => window.untrusted), undefined);
  failFeed = true; await refresh(); await page.getByText('목록을 확인하지 못했어요.', { exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '같은 글 수정하기', exact: true }).count(), 1); failFeed = false;
  record('rejected text can be corrected in the same slot; text is escaped and list failures retain the private record');
  // Only synthetic approved feed entries; own rejected/pending text must stay outside the lake.
  const publicWish = (index) => ({ id:500-index, content:`가상 공개 소원 ${index+1}`, templateId:'PLAIN', style:'ROSE', anonymous:true, nickname:null, profileId:null, luckScore:null, landingZone:null });
  feeds.wishes.items = Array.from({length:20}, (_, index) => publicWish(index));
  feeds.wishes.nextCursor = feeds.wishes.items.at(-1).id;
  olderWishes = Array.from({length:5}, (_, index) => publicWish(index + 20));
  await refresh();
  await page.getByRole('button', {name:'소원 1 열기',exact:true}).waitFor();
  await page.waitForFunction(() => document.querySelector('.wish-gallery .fountain-letter')?.naturalWidth > 0);
  assert.equal(await page.locator('.wish-lake-coin').count(),10);
  assert.equal(await page.locator('.wish-coin-gold').count(),3);
  assert.equal(await page.locator('.wish-coin-shadow').count(),7);
  const shadow = page.locator('.wish-coin-shadow').first();
  assert.ok(await shadow.evaluate(el => el.getAnimations().some(animation => animation.playState === 'running')));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  assert.equal(await shadow.evaluate(el => el.getAnimations().length),0);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  assert.equal(await page.locator('.wish-gallery').getByText('비공개 검수 중인 테스트 수정본',{exact:true}).count(),0);
  for (const width of [360,390,430,1440]) {
    await page.setViewportSize({width,height:900});
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth),false);
    const boxes=await page.locator('.wish-lake-coin').evaluateAll(nodes => nodes.map(node => {const r=node.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height};}));
    for(let i=0;i<boxes.length;i++) {
      assert.ok(boxes[i].width>=44 && boxes[i].height>=44);
      for(let j=i+1;j<boxes.length;j++) assert.ok(boxes[i].right<=boxes[j].x || boxes[j].right<=boxes[i].x || boxes[i].bottom<=boxes[j].y || boxes[j].bottom<=boxes[i].y);
    }
    await page.screenshot({path:resolve(output,`wish-lake-${width}.png`),fullPage:true});
  }
  await page.getByRole('button',{name:'소원 1 열기',exact:true}).focus(); await page.keyboard.press('Enter');
  await page.getByRole('heading',{name:'발견한 소원',exact:true}).waitFor();
  assert.equal(await page.getByRole('heading',{name:'발견한 소원'}).evaluate(el => el===document.activeElement),true);
  await page.getByRole('button',{name:'댓글 보기',exact:true}).click();
  await page.getByLabel('동료의 소원에 댓글 남기기',{exact:true}).waitFor();
  await page.getByRole('button',{name:'소원 닫기',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'소원 1 열기',exact:true}).evaluate(el => el===document.activeElement),true);
  await page.getByRole('button',{name:'소원 4 열기',exact:true}).click();
  await page.locator('.wish-lake-detail').getByText('가상 공개 소원 4', { exact: true }).waitFor();
  await page.getByRole('button',{name:'다음 동전',exact:true}).click();
  await page.getByRole('button',{name:'소원 11 열기',exact:true}).waitFor();
  await page.getByRole('button',{name:'소원 더 불러오기',exact:true}).click();
  await page.getByRole('button',{name:'소원 21 열기',exact:true}).waitFor();
  assert.equal(await page.locator('.wish-lake-coin').count(),5);
  await page.getByRole('button',{name:'구름으로 보기',exact:true}).click();
  const clouds = page.getByRole('list',{name:'구름 소원 목록'});
  assert.equal(await clouds.getByRole('listitem').count(),25);
  assert.equal(await page.locator('.wish-sky-flight').isVisible(), true);
  assert.deepEqual(await page.getByRole('group',{name:'소원 보기 방식'}).getByRole('button').allTextContents(), ['분수로 보기', '구름으로 보기']);
  await clouds.getByRole('button').last().press('Enter');
  await page.locator('.wish-lake-detail').getByText('가상 공개 소원 25', { exact: true }).waitFor();
  await page.getByRole('button',{name:'소원 닫기',exact:true}).click();
  assert.equal(await clouds.getByRole('button').last().evaluate(el => el === document.activeElement),true);
  await page.screenshot({path:resolve(output,'wish-cloud-1440.png'),fullPage:true});
  await page.getByRole('button',{name:'분수로 보기',exact:true}).click();
  feeds.wishes={items:[],nextCursor:null}; await refresh(); await page.getByRole('button',{name:'소원 21 열기',exact:true}).waitFor({state:'hidden'}); assert.equal(await page.locator('.wish-lake-coin').count(),0);
  record('three stacked coins and seven drifting shadows page without overlap, respect reduced motion and open accessible details');
  // Unload the unlocked screen before changing fixtures: its refresh can enqueue a feed
  // after a network-idle observation. Locked-screen requests are measured independently.
  await page.goto('about:blank');
  locked = true;
  for (const width of [360,390,430,768,1440]) {
    await page.setViewportSize({width,height:900});
    for (const slug of ['make-a-wish','say-something']) {
      const readsBefore = contentReads;
      await visit('/missions/'+slug); await page.waitForLoadState('networkidle');
      await page.getByText('현장의 NFC 태그를 찾아 휴대폰을 가까이 대주세요.',{exact:true}).waitFor();
      assert.equal(await page.locator('.fountain-art, .wish-lake-intro, .content-mission, #content-text').count(),0);
      assert.equal(contentReads,readsBefore,'locked content never requests a feed or private slot: '+JSON.stringify(contentRequests.slice(readsBefore)));
      await page.screenshot({path:resolve(output,`locked-${slug}-${width}.png`),fullPage:true});
    }
  }
  assert.deepEqual(errors, []); assert.deepEqual(routeErrors, []); record('locked wish and cheer missions hide the fountain, feed and form without content API requests at five widths');
  await writeFile(resolve(output, 'content-contract-checks.json'), JSON.stringify({ kind: 'explicit browser API fixtures; no live submissions or moderation decisions', checks, errors, routeErrors }, null, 2));
} finally { await browser?.close(); await server.httpServer.close(); }
