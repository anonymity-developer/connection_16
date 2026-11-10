/* global document, window */
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Production-build UI contract, local read-only fixtures only. No real account or operational write.
const server = await preview({ preview: { host: '127.0.0.1', port: 4475, strictPort: true } });
let browser;
try {
  browser = await chromium.launch();
  const page = await browser.newPage();
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  let signedIn = false, admin = false, currentState = 'LOCKED', adminReads = 0;
  await page.route('**/*', async route => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== 'http://127.0.0.1:4475') return route.abort();
    if (!url.pathname.startsWith('/api/v1/')) return route.continue();
    assert.ok(req.method() === 'GET' || new URL(req.url()).pathname === '/api/v1/missions/show-me/reconcile', 'only fixture reconciliation may write');
    const path = url.pathname.slice('/api/v1'.length);
    if (path === '/csrf') return route.fulfill({status:200,json:{headerName:'X-XSRF-TOKEN',token:'polish-fixture'}});
    if (path === '/missions/show-me/reconcile') { assert.equal(req.headers()['x-xsrf-token'],'polish-fixture'); return route.fulfill({status:200,json:{newlyCompleted:false}}); }
    let body;
    if (path === '/auth/config') body = { available: true, passwordMinLength: 15, passwordMaxLength: 64 };
    else if (path === '/users/me') {
      if (!signedIn) return route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' } });
      body = { id: 1, username: 'ui-fixture', nickname: 'UI 검사', onboardingRequired: false };
    } else if (path === '/tags/pending') body = { present: false };
    else if (path === '/event') body = { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false };
    else if (path === '/missions/collection') body = { missions: codes.map((code, i) => ({ id: i + 1, code, state: currentState, active: true })), completedCount: currentState === 'COMPLETED' ? 5 : 0, completion: { complete: currentState === 'COMPLETED', completionOrder: 1 } };
    else if (path === '/admin/access') body = { admin };
    else if (path.startsWith('/admin/')) { adminReads++; body = { items: [], nextCursor: null }; }
    else if (path === '/discovery/graph') body = { self: { id: 1, nickname: 'UI 검사' }, nodes: [], edges: [], truncated: false };
    else return route.fulfill({ status: 503, json: { code: 'TEST_OUT_OF_SCOPE', message: '본문 데이터는 이 UI 검사의 범위가 아니에요.' } });
    return route.fulfill({ status: 200, json: body });
  });
  async function open(path) { await page.goto('http://127.0.0.1:4475' + path); await page.getByRole('link', { name: 'TAG ME 홈' }).waitFor(); await page.waitForLoadState('networkidle'); }
  async function fits() { assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'no page horizontal overflow'); }
  for (const width of [360, 375, 390, 412, 430, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    for (const path of ['/', '/missions', '/admin', '/discovery', '/me']) { await open(path); await fits(); }
    assert.equal(adminReads, 0, 'signed-out routes never fetch operational content');
    await open('/');
    assert.equal(await page.locator('.letter-slots .letter-sticker img').count(), 5);
    assert.equal(await page.locator('.collection-bubble').count(), 1);
    const bubble = await page.locator('.collection-bubble').boundingBox();
    const slots = await page.locator('.letter-slots').boundingBox();
    assert.ok(bubble.x >= 0 && bubble.x + bubble.width <= width && bubble.y + bubble.height + 5 <= slots.y, 'bubble fits and does not cover characters');
    assert.equal(await page.locator('.mission-list > .mission-row').count(), 5);
    assert.equal(await page.locator('.mission-overview-card').count(), 0);
    assert.deepEqual(await page.locator('.slot-label').allTextContents(), ['소개', '취향', '소원', '탭', '응원']);
    assert.equal(await page.locator('.collection-count, .home-help').count(), 0);
    await open('/missions');
    await page.getByRole('heading', { name: '미션 현황·힌트', exact: true }).waitFor();
    assert.equal(await page.locator('.mission-overview-list .mission-progress-guide').count(), 5);
    await fits();
    await open('/');
    const y = await page.locator('.mission-list > [data-mission="SAY_SOMETHING"]').boundingBox();
    const first = await page.locator('.mission-list > [data-mission="SHOW_ME"]').boundingBox();
    if (width > 640) assert.ok(Math.abs(y.width - first.width) <= 1 && Math.abs(y.x - first.x) <= 1, 'fifth card occupies the left half of the last row');
    else assert.ok(Math.abs(y.width - first.width) <= 1, 'mobile cards stack at full width');
    assert.equal(await page.locator('.mission-shortcut, .preparation-note').count(), 0);
    assert.equal(await page.locator('.home-overview .next-panel').count(), 0);
    const board = await page.locator('.sticker-board').boundingBox();
    const overviewArea = await page.locator('.home-overview').boundingBox();
    assert.ok(Math.abs(board.width-overviewArea.width)<=1, 'collection fills the overview');
    await page.locator('.site-footer').scrollIntoViewIfNeeded();
    await page.getByText('기획·제작 | 서울 지역대표 이다연 · 백지원', { exact: true }).waitFor();
    if (width <= 430) {
      const footer = await page.locator('.site-footer').boundingBox(), nav = await page.locator('.mobile-nav').boundingBox();
      assert.ok(footer.y + footer.height <= nav.y + 1, 'fixed navigation clears the footer');
    }
    signedIn = true;
    for (const state of ['LOCKED', 'IN_PROGRESS', 'COMPLETED']) {
      currentState = state;
      await open('/');
      assert.equal(await page.locator('.collection-bubble').count(), state === 'COMPLETED' ? 0 : 1);
      assert.equal(await page.locator('.letter-slots .is-uncollected').count(), state === 'COMPLETED' ? 0 : 5);
      const visuals = await page.locator('.letter-slots .letter-slot').evaluateAll(nodes => nodes.map(node => node.getAttribute('data-visual')));
      assert.deepEqual(visuals, codes.map(code => state === 'COMPLETED' ? 'collected' : state === 'LOCKED' || code === 'SHOW_ME' ? 'unrevealed' : 'unlocked'), 'collection visual matches server mission state');
      const imgFilter = await page.locator('.letter-slots .letter-slot:first-child .letter-sticker img').evaluate(el => window.getComputedStyle(el).filter);
      assert.ok(state === 'COMPLETED' ? imgFilter === 'none' : imgFilter.includes('grayscale'), 'unearned character stays gray');
      for (const code of codes) {
        await open('/missions/' + code.toLowerCase().replaceAll('_', '-'));
        await fits();
        const card = page.locator('.mission-progress-guide');
        await card.getByText('나의 진행 현황', { exact: true }).waitFor();
        const summary = card.getByText('미션 힌트 보기', { exact: true });
        await summary.focus(); await page.keyboard.press('Enter');
        assert.equal(await card.locator('details').getAttribute('open'), '');
        assert.equal(await card.locator('.letter-sticker.is-collected').count(), state === 'COMPLETED' ? 1 : 0);
      }
    }
    await open('/admin'); assert.equal(adminReads, 0, 'non-admin account never mounts workspace');
    admin = true;
    for (const tab of ['moderation', 'content', 'audit']) { await open('/admin/moderation?tab=' + tab); await page.locator('.ops-sheet').waitFor(); await fits(); }
    admin = false; signedIn = false; currentState = 'LOCKED'; adminReads = 0;
  }
  assert.deepEqual(errors, []);
  console.log('PASS: production UI widths 360/375/390/412/430/1440, five-card layout, three collection visuals, navigation clearance, five mission hints/states and admin read gates (fixtures only).');
} finally { await browser?.close(); await new Promise(resolve => server.httpServer.close(resolve)); }
