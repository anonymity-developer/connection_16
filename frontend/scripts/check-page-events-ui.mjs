/* global document, innerWidth, getComputedStyle */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { chmod, mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Intercepted synthetic API contracts only. Never contact a backend, create a
// participant, or put example events into production assets or an actual DB.
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
const snapshots = {};
const server = await preview({ preview: { host: '127.0.0.1', port: 4679, strictPort: true }, plugins: [{ name: 'page-events-measured-dom', configurePreviewServer(server) {
  server.middlewares.use((req, res, next) => {
    const html = snapshots[req.url]; if (!html) return next();
    res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html);
  });
} }] });
let browser;
const today = new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10);
const records = Array.from({ length: 52 }, (_, i) => ({
  ref: `PAGE:${52 - i}`, recordedAt: new Date(Date.now() - i * 1000 - 60000).toISOString(), eventAt: new Date(Date.now() - i * 1000 - 62000).toISOString(),
  screenId: i % 2 ? 'SYNC_ME' : 'HOME', screenName: i % 2 ? 'SYNC ME' : '홈·SSAFY 수집판',
  eventType: i === 1 ? 'MISSION_SUBMIT_SUCCESS' : i === 2 ? 'MISSION_STEP_VIEW' : 'PAGE_VIEW',
  mission: i === 1 || i === 2 ? 'SYNC_ME' : null, step: i === 2 ? 'QUESTION_1' : null,
  participantRef: `P-${71 + i % 3}`, sessionRef: i === 2 ? null : 'c'.repeat(16), source: i === 1 ? 'SERVER' : i === 2 ? 'SYNC' : 'CLIENT',
}));
records[2].screenId = 'SYNC_ME'; records[2].screenName = 'SYNC ME'; records[2].ref = 'SYNC:1';
const screens = [{ id: 'HOME', name: '홈·SSAFY 수집판', mission: null }, { id: 'SYNC_ME', name: 'SYNC ME', mission: 'SYNC_ME' }];
const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
const checks = [], errors = [], unexpected = [], mutations = [], requests = [];
const record = text => { checks.push(text); console.log(`PASS ${text}`); };
let mode = 'normal', allow = true, signedIn = true, releaseAccess, accessGate = new Promise(done => { releaseAccess = done; }), releaseLog, logGate = null, onLogHeld;
function dataFor(url) {
  const window = { from: url.searchParams.get('from'), to: url.searchParams.get('to') };
  const rows = mode === 'empty' ? [] : mode === 'visual' ? records.slice(0, 4) : records;
  return { window, rows: rows.filter(row => row.recordedAt >= window.from && row.recordedAt < window.to) };
}
function summary(window, rows) {
  const pageEvents = rows.filter(row => row.eventType === 'PAGE_VIEW');
  const pages = screens.flatMap(screen => {
    const visits = pageEvents.filter(row => row.screenId === screen.id); if (!visits.length) return [];
    const people = new Set(visits.map(row => row.participantRef));
    return [{ screenId: screen.id, name: screen.name, views: visits.length, visitors: people.size, repeatVisitors: [...people].filter(person => visits.filter(row => row.participantRef === person).length > 1).length }];
  });
  const days = [...new Set(pageEvents.map(row => new Date(Date.parse(row.recordedAt) + 9 * 3600000).toISOString().slice(0, 10)))].sort().map(date => {
    const visits = pageEvents.filter(row => new Date(Date.parse(row.recordedAt) + 9 * 3600000).toISOString().slice(0, 10) === date);
    return { date, views: visits.length, visitors: new Set(visits.map(row => row.participantRef)).size };
  });
  const edges = new Map(), previous = new Map();
  for (const row of [...pageEvents].reverse()) {
    const key = `${row.participantRef}:${row.sessionRef}`, prior = previous.get(key);
    if (prior) { const edge = `${prior.screenId}:${row.screenId}`, current = edges.get(edge) ?? { fromScreen: prior.screenId, toScreen: row.screenId, moves: 0, people: new Set() }; current.moves++; current.people.add(row.participantRef); edges.set(edge, current); }
    previous.set(key, row);
  }
  return { window, collectionStartedAt: '2026-11-16T00:00:00Z', syncCollectionStartedAt: '2026-11-10T00:00:00Z', droppedCompletions: mode === 'visual' ? 2 : 0,
    screens, pages, days, flows: [...edges.values()].map(({ people, ...edge }) => ({ ...edge, users: people.size })),
    funnels: codes.map(mission => ({ mission, stages: ['미션 진입', '주요 단계', '서버 저장 완료'].map((name, i) => {
      const distinct = type => new Set(rows.filter(row => row.screenId === 'SYNC_ME' && row.eventType === type).map(row => row.participantRef)).size;
      // Explicit rate edge-case response, tested separately from the visual dataset.
      const values = mission !== 'SYNC_ME' ? [0, 0, 0] : mode === 'rates' ? [10, 8, 9] : ['PAGE_VIEW', 'MISSION_STEP_VIEW', 'MISSION_SUBMIT_SUCCESS'].map(distinct);
      const users = values[i], entryConversion = values[0] ? users / values[0] * 100 : null;
      const previousConversion = i === 0 || !values[i - 1] ? null : users / values[i - 1] * 100;
      return { name, users, entryConversion, previousConversion, estimatedDropOff: previousConversion === null ? null : 100 - previousConversion };
    }) })) };
}
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.slice(7);
    const reply = (status, json) => route.fulfill({ status, json });
    if (req.method() !== 'GET') { mutations.push(path); return reply(500, { message: 'An analytics screen attempted a write.' }); }
    if (path === '/auth/config') return reply(200, { available: true, passwordMinLength: 8, passwordMaxLength: 20 });
    if (path === '/users/me') return signedIn ? reply(200, { id: 7, username: 'admin:analytics-fixture', nickname: null, onboardingRequired: false, admin: true }) : reply(401, { code: 'UNAUTHENTICATED' });
    if (path === '/tags/pending') return reply(200, { present: false });
    if (path === '/admin/access') { await accessGate; return allow ? reply(200, { admin: true }) : reply(403, { code: 'ADMIN_REQUIRED' }); }
    if (path.startsWith('/admin/events')) {
      requests.push({ path, ...Object.fromEntries(url.searchParams) });
      if (mode === 'error') return reply(503, { code: 'ANALYTICS_UNAVAILABLE' });
      if (mode === 'forbidden') return reply(403, { code: 'ADMIN_REQUIRED' });
      if (mode === 'unauthorized') return reply(401, { code: 'SESSION_EXPIRED' });
      const { window, rows } = dataFor(url);
      // The cutoff is captured by the browser. Compare with that same clock,
      // rather than a second process's wall-clock sampling on Windows.
      assert.ok(Date.parse(window.to) <= await page.evaluate(() => Date.now()), `future cutoff ${window.to}`);
      assert.ok(Date.parse(window.to) > Date.parse(window.from));
      assert.ok(Date.parse(window.to) - Date.parse(window.from) <= 31 * 86400000);
      if (path.endsWith('/summary')) return reply(200, summary(window, rows));
      if (logGate) { const gate = logGate; logGate = null; onLogHeld?.(); onLogHeld = null; await gate; }
      const filtered = rows.filter(row => (!url.searchParams.has('screen') || row.screenId === url.searchParams.get('screen')) && (!url.searchParams.has('type') || row.eventType === url.searchParams.get('type')));
      const offset = Number(url.searchParams.get('offset')), limit = Number(url.searchParams.get('limit'));
      assert.equal(limit, 50);
      return reply(200, { window, items: filtered.slice(offset, offset + limit), total: filtered.length, offset, nextOffset: offset + limit < filtered.length ? offset + limit : null });
    }
    if (path === '/admin/funnel') return reply(200, { mission: 'SYNC_ME', questionnaireVersion: 'isolated-ui-fixture', collectionStartedAt: records[0].recordedAt, generatedAt: records[0].recordedAt, steps: [] });
    unexpected.push(path); return reply(500, { message: 'Unexpected isolated API request' });
  });
  const visit = path => page.goto(`http://127.0.0.1:4679${path}`);
  const ready = async () => { await page.locator('.admin-events[aria-busy="false"]').waitFor(); };
  const tab = label => page.getByRole('navigation', { name: '이벤트 분석 보기' }).getByRole('link', { name: label, exact: true });
  await visit('/admin/events'); await page.getByText('관리자 권한을 확인하고 있어요.', { exact: true }).waitFor();
  assert.equal(requests.length, 0); releaseAccess(); await ready();
  assert.equal(await page.locator('.events-log-table tbody tr').count(), 50);
  assert.equal(await page.getByRole('heading', { name: '페이지 이벤트 분석', exact: true }).count(), 1);
  assert.equal(await page.getByText('기록 없음', { exact: true }).count(), 1);
  assert.ok(await page.getByText(/^실제 완료 /).count());
  assert.ok(await page.locator('body').getByText('MISSION_SUBMIT_SUCCESS', { exact: true }).count());
  record('ADMIN verification gates all analysis requests; raw rows show server time, type, internal ID and unknown legacy session');
  const until = requests.find(row => row.path === '/admin/events').to;
  await page.getByRole('button', { name: '다음', exact: true }).click(); await ready();
  assert.equal(await page.locator('.events-log-table tbody tr').count(), 2);
  assert.equal(requests.at(-1).offset, '50'); assert.equal(requests.at(-1).to, until);
  assert.ok(await page.getByRole('button', { name: '다음', exact: true }).isDisabled());
  await page.getByRole('button', { name: '이전', exact: true }).click(); await ready();
  assert.equal(await page.locator('.events-log-table tbody tr').count(), 50);
  await page.getByLabel('페이지', { exact: true }).selectOption('SYNC_ME');
  await page.getByLabel('이벤트 유형', { exact: true }).selectOption('MISSION_STEP_VIEW');
  await page.getByRole('button', { name: '조회', exact: true }).click(); await ready();
  assert.equal(await page.locator('.events-log-table tbody tr').count(), 1); assert.equal(requests.at(-1).offset, '0');
  assert.equal(await page.getByText('QUESTION_1', { exact: false }).count(), 1);
  record('server pagination retains the exclusive end; page/type filters are sent to the API and reset the offset');
  const beforeInvalid = requests.length;
  await page.getByLabel('시작일(KST)').fill('2026-01-01'); await page.getByLabel('종료일(KST)').fill('2026-02-01');
  await page.getByRole('button', { name: '조회', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '최대 31일' }).waitFor(); assert.equal(requests.length, beforeInvalid);
  await page.getByLabel('시작일(KST)').fill(today); await page.getByLabel('종료일(KST)').fill(today);
  await page.getByLabel('페이지', { exact: true }).selectOption(''); await page.getByLabel('이벤트 유형', { exact: true }).selectOption('');
  await page.getByRole('button', { name: '조회', exact: true }).click(); await ready();
  const held = new Promise(done => { releaseLog = done; }); logGate = held;
  await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await page.getByText('이벤트 분석을 불러오고 있어요.', { exact: true }).waitFor();
  assert.ok(await page.getByRole('button', { name: '조회', exact: true }).isDisabled());
  releaseLog(); await ready(); record('invalid periods never fetch, and loading prevents duplicate actions');
  const oldHeld = new Promise(done => { onLogHeld = done; });
  logGate = new Promise(done => { releaseLog = done; });
  await page.getByRole('button', { name: '새로고침', exact: true }).click(); await oldHeld;
  await tab('페이지 방문').click(); await ready();
  const oldResponse = page.waitForResponse(response => response.url().includes('/admin/events?'));
  releaseLog(); await oldResponse;
  assert.equal(await page.locator('.events-log-table').count(), 0);
  assert.equal(await page.getByRole('region', { name: '페이지별 방문', exact: true }).count(), 1);
  await tab('이벤트 로그').click(); await ready();
  record('a late response from an abandoned log tab cannot replace the current analytics view');
  mode = 'error'; await page.getByRole('button', { name: '새로고침', exact: true }).click(); await ready();
  await page.getByRole('alert').filter({ hasText: '이벤트 분석을 불러오지 못했어요.' }).waitFor();
  assert.equal(await page.locator('.events-log-table').count(), 0);
  mode = 'normal'; await page.getByRole('button', { name: '다시 불러오기', exact: true }).click(); await ready();
  mode = 'empty'; await page.getByRole('button', { name: '새로고침', exact: true }).click(); await ready();
  await page.getByText('조회 기간에 수집된 이벤트가 없어요.', { exact: true }).waitFor();
  await tab('미션 퍼널').click(); await ready();
  assert.equal(await page.locator('.events-stats-table').count(), 5);
  assert.equal(await page.locator('.events-stats-table td.num').filter({ hasText: /^0명$/ }).count(), 15);
  assert.ok(await page.locator('.events-stats-table td.num').filter({ hasText: /^—$/ }).count());
  record('empty counts remain zero, missing denominators remain dashes, and collector errors clear rows with a retry');
  mode = 'rates'; await page.getByRole('button', { name: '새로고침', exact: true }).click(); await ready();
  const sync = page.getByRole('region', { name: 'SYNC_ME 퍼널', exact: true });
  assert.equal(await sync.getByText('112.5%', { exact: true }).count(), 1);
  assert.equal(await sync.getByText('-12.5%', { exact: true }).count(), 1);
  await sync.getByRole('link', { name: 'SYNC 질문별 상세' }).click(); await page.getByRole('heading', { name: '질문별 참여 흐름' }).waitFor();
  await page.goBack(); await ready();
  record('all five independent mission funnels retain inverted rates and link to the existing question funnel without reimplementing it');
  mode = 'visual'; await tab('이벤트 로그').click(); await ready(); await page.getByRole('button', { name: '새로고침', exact: true }).click(); await ready();
  for (const width of [360, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    if (width === 360) {
      assert.ok(!await page.getByRole('navigation', { name: '관리자 메뉴' }).isVisible()); await page.getByRole('button', { name: '메뉴', exact: true }).click(); await page.getByRole('navigation', { name: '관리자 메뉴' }).waitFor(); await page.getByRole('button', { name: '메뉴', exact: true }).click();
      assert.ok(!await page.getByLabel('시작일(KST)').isVisible());
      await page.getByRole('button', { name: '조회 조건', exact: true }).click(); await page.getByLabel('시작일(KST)').waitFor();
      assert.equal(await page.getByRole('button', { name: '조회 조건', exact: true }).getAttribute('aria-expanded'), 'true');
      await page.screenshot({ path: resolve(output, 'page-events-filters-360.png'), fullPage: true });
      await page.getByRole('button', { name: '조회 조건', exact: true }).click();
    }
    for (const label of ['이벤트 로그', '페이지 방문', '이동 흐름', '미션 퍼널']) {
      await tab(label).click(); await ready();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${label} at ${width}`);
      assert.equal(await page.locator('h1').innerText(), '페이지 이벤트 분석');
      const metrics = await page.locator('.events-stats-table .num').evaluateAll(cells => cells.map(cell => ({ align: getComputedStyle(cell).textAlign, figures: getComputedStyle(cell).fontVariantNumeric })));
      assert.ok(metrics.every(cell => cell.align === 'right' && cell.figures.includes('tabular-nums')));
      const key = { '이벤트 로그': 'logs', '페이지 방문': 'pages', '이동 흐름': 'flows', '미션 퍼널': 'funnels' }[label];
      await page.screenshot({ path: resolve(output, `page-events-${key}-${width}.png`), fullPage: true });
      if (width === 1440) snapshots[`/events-snapshot-${key}.html`] = (await page.content()).replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
    }
  }
  record('four actual analytics views render at 360/390/430/768/1440px, with folded navigation and right-aligned tabular values');
  const requestsBeforeBack = requests.length; await page.goBack(); await ready(); assert.ok(requests.length >= requestsBeforeBack);
  mode = 'forbidden'; allow = false; await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '관리자 권한을 다시 확인해 주세요.' }).waitFor();
  assert.equal(await page.locator('.admin-events').count(), 0);
  await page.reload(); await page.getByRole('alert').waitFor();
  const afterDenied = requests.length; await page.waitForLoadState('networkidle'); assert.equal(requests.length, afterDenied);
  mode = 'normal'; allow = true; await page.reload(); await ready();
  signedIn = false; mode = 'unauthorized'; await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await page.getByRole('heading', { name: '운영자 로그인', exact: true }).waitFor(); assert.equal(await page.locator('.admin-events').count(), 0);
  record('403 revocation and 401 expiry remove all private rows; no admin or participant writes are issued');
  assert.deepEqual(mutations, []); assert.deepEqual(unexpected, []); assert.deepEqual(errors, []);
  await writeFile(resolve(output, 'page-events-contract-checks.json'), JSON.stringify({ kind: 'isolated synthetic browser contracts; no actual API/DB', checks, errors, mutations, requests }, null, 2));
  for (const view of ['logs', 'pages']) {
    const out = `.taste-skill/page-events-${view}-render`;
    // Match the existing admin inspector's launcher on ephemeral Linux runners.
    const inspector = resolve('..', out); await mkdir(inspector, { recursive: true });
    const launcher = process.platform === 'win32' ? chromium.executablePath() : resolve(inspector, 'chromium.sh');
    if (process.platform !== 'win32') { await writeFile(launcher, `#!/usr/bin/env bash\nexec "${chromium.executablePath()}" --no-sandbox "$@"\n`); await chmod(launcher, 0o700); }
    await new Promise((done, reject) => {
      const child = spawn(process.execPath, ['scripts/taste.mjs', 'inspect', `http://127.0.0.1:4679/events-snapshot-${view}.html`, '--app', '--widths', '1440,768,390', '--out', out], { cwd: resolve('..'), env: { ...process.env, CHROME_PATH: launcher }, stdio: 'inherit' });
      child.on('error', reject); child.on('exit', code => code === 0 ? done() : reject(new Error(`Events ${view} inspector exited ${code}`)));
    });
    const report = JSON.parse(await readFile(resolve('..', out, 'report.json'), 'utf8'));
    assert.ok(report.viewports.every(viewport => viewport.metrics.h1.text === '페이지 이벤트 분석'), 'inspector must measure the actual approved analytics page');
    assert.equal(report.findings.filter(finding => finding.level === 'FAIL').length, 0);
  }
} finally { await browser?.close(); await new Promise(done => server.httpServer.close(done)); }
