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
    assert.equal(req.method(), 'GET', 'UI check must never mutate even fixture operations');
    const path = url.pathname.slice('/api/v1'.length);
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
  for (const width of [360, 390, 430, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    for (const path of ['/', '/missions', '/admin', '/discovery', '/me']) { await open(path); await fits(); }
    assert.equal(adminReads, 0, 'signed-out routes never fetch operational content');
    await open('/');
    assert.equal(await page.locator('.letter-slots .letter-sticker img').count(), 5);
    assert.equal(await page.locator('.mission-list > .mission-row').count(), 6);
    assert.deepEqual(await page.locator('.slot-label').allTextContents(), ['소개', '취향', '소원', '탭', '응원']);
    assert.equal(await page.locator('.collection-count, .home-help').count(), 0);
    await page.locator('.mission-overview-card').click();
    await page.getByRole('heading', { name: '미션 현황·힌트', exact: true }).waitFor();
    assert.equal(await page.locator('.mission-overview-list .mission-progress-guide').count(), 5);
    await fits();
    await open('/');
    const y = await page.locator('.mission-list > [data-mission="SAY_SOMETHING"]').boundingBox();
    const overview = await page.locator('.mission-overview-card').boundingBox();
    if (width >= 1100) assert.ok(Math.abs(y.width - overview.width) <= 1 && Math.abs(y.y - overview.y) <= 1, 'Y and overview share an equal-width last row');
    else assert.ok(overview.y >= y.y + y.height, 'mobile cards stack vertically');
    assert.equal(await page.locator('.mission-shortcut, .preparation-note').count(), 0);
    if (width >= 1100) {
      const board = await page.locator('.sticker-board').boundingBox(), cta = await page.locator('.next-panel').boundingBox();
      assert.ok(cta.x >= board.x + board.width, 'wide home overview has two columns');
    }
    await page.locator('.site-footer').scrollIntoViewIfNeeded();
    await page.getByText('기획·제작 | 서울 지역대표 이다연 · 백지원', { exact: true }).waitFor();
    if (width <= 430) {
      const footer = await page.locator('.site-footer').boundingBox(), nav = await page.locator('.mobile-nav').boundingBox();
      assert.ok(footer.y + footer.height <= nav.y + 1, 'fixed navigation clears the footer');
    }
    signedIn = true;
    for (const state of ['LOCKED', 'IN_PROGRESS', 'COMPLETED']) {
      currentState = state;
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
    for (const tab of ['moderation', 'content', 'audit']) { await open('/admin?tab=' + tab); await page.locator('.ops-sheet').waitFor(); await fits(); }
    admin = false; signedIn = false; currentState = 'LOCKED'; adminReads = 0;
  }
  assert.deepEqual(errors, []);
  console.log('PASS: production UI widths 360/390/430/1280, overview, navigation clearance, empty states, five mission hints/states and admin read gates (fixtures only).');
} finally { await browser?.close(); await new Promise(resolve => server.httpServer.close(resolve)); }
