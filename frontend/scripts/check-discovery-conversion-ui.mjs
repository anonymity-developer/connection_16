/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir, writeFile, chmod, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { preview } from 'vite';
import { chromium } from 'playwright';

// Production build with in-process synthetic HTTP fixtures only. No live records.
const output = resolve('../output/playwright');
await mkdir(output, { recursive: true });
let browser, snapshot = '';
const server = await preview({ preview: { host: '127.0.0.1', port: 4576, strictPort: true }, plugins: [{
  name: 'conversion-measured-dom', configurePreviewServer(server) {
    server.middlewares.use('/discovery-conversion-snapshot.html', (request, response) => {
      if (request.method !== 'GET') { response.statusCode = 405; response.end(); return; }
      response.setHeader('Content-Type', 'text/html; charset=utf-8'); response.end(snapshot);
    });
  },
}] });
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  let signedIn = false, allowed = true, mode = 'normal', reads = 0, releaseRead, signalRead;
  const mutations = [], errors = [], checks = [];
  page.on('pageerror', error => errors.push(error.message));
  const codes = ['SYNC_COMPLETED', 'PROFILE_FIRST_VISIT', 'MUTUAL_VISIT'];
  function report(counts) {
    return { basis: 'CURRENT_SYNC_RESULTS_ALL_VERSIONS', generatedAt: '2026-11-17T00:00:00Z', steps: counts.map((users, index) => ({
      code: codes[index], users,
      previousConversionRate: index === 0 || counts[index - 1] === 0 ? null : 100 * users / counts[index - 1],
      completionConversionRate: counts[0] === 0 ? null : 100 * users / counts[0],
    })) };
  }
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.slice('/api/v1'.length);
    const reply = (status, body) => route.fulfill({ status, ...(body === undefined ? {} : { json: body }) });
    if (request.method() !== 'GET') {
      mutations.push(path);
      if (path === '/auth/login') { signedIn = true; return reply(200, { id: 1, username: 'admin:contract', nickname: null, onboardingRequired: false, admin: true }); }
      return reply(500, { message: 'Read-only analysis attempted a write.' });
    }
    if (path === '/auth/config') return reply(200, { available: true, participantAvailable: false, adminAvailable: true, passwordMinLength: 8, passwordMaxLength: 20 });
    if (path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'conversion-test-csrf' });
    if (path === '/users/me') return signedIn ? reply(200, { id: 1, username: 'admin:contract', nickname: null, onboardingRequired: false, admin: true }) : reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' });
    if (path === '/tags/pending') return reply(200, { present: false });
    if (!signedIn || !allowed) return reply(403, { code: 'ADMIN_REQUIRED', message: '관리자 권한이 필요해요.' });
    if (path === '/admin/access') return reply(200, { admin: true });
    if (path === '/admin/overview') return reply(200, { members: 0, activeMembers: 0, completeMembers: 0, activeSessions: 0, connectedMembers: 0, connectionSeconds: null, trackedSince: null, missions: [], generatedAt: '2026-11-17T00:00:00Z' });
    if (path === '/admin/discovery-conversion') {
      reads++;
      if (mode === 'error') return reply(503, { message: '전환 분석을 불러오지 못했어요.' });
      if (mode === 'forbidden') return reply(403, { message: '관리자 권한이 필요해요.' });
      if (mode === 'loading') { await new Promise(done => { releaseRead = done; signalRead(); }); }
      return reply(200, report(mode === 'empty' ? [0, 0, 0] : mode === 'no-visits' ? [4, 0, 0] : [10, 6, 3]));
    }
    return reply(500, { message: `Unexpected conversion fixture GET ${path}` });
  });
  const visit = path => page.goto('http://127.0.0.1:4576' + path);
  const record = name => { checks.push(name); console.log('PASS ' + name); };
  await visit('/auth'); await page.getByLabel('아이디', { exact: true }).fill('admin:contract');
  await page.getByLabel('비밀번호', { exact: true }).fill('a contract test password');
  await page.getByRole('button', { name: '로그인하기', exact: true }).click(); await page.waitForURL('**/admin');
  await page.getByRole('link', { name: '동료 탐색 전환 분석', exact: true }).click();
  await page.getByRole('heading', { name: 'SYNC ME 이후 탐색 흐름', exact: true }).waitFor();
  await page.getByText('3명', { exact: true }).waitFor();
  const rows = page.locator('.admin-conversion-table tbody tr');
  assert.equal(await rows.count(), 3);
  assert.deepEqual(await rows.last().locator('td').allTextContents(), ['해당 동료와 상호 방문 형성', '3명', '50.0%', '30.0%']);
  await page.getByText('프로필 열기 요청의 저장 기록이며 추천 노출·실제 클릭·실제 대화나 설문의 인과 효과를 뜻하지 않습니다.', { exact: false }).waitFor();
  record('three ordered stages show unique users and both conversion denominators with the interpretation limits');

  mode = 'loading'; const started = new Promise(done => { signalRead = done; });
  await page.getByRole('button', { name: '새로고침', exact: true }).click(); await started;
  await page.getByRole('status').filter({ hasText: '전환 분석을 불러오고 있어요.' }).waitFor();
  assert.equal(await page.getByRole('button', { name: '새로고침', exact: true }).isDisabled(), true);
  assert.equal(await page.getByText('3명', { exact: true }).count(), 1);
  mode = 'normal'; releaseRead(); await page.locator('.admin-conversion-panel[aria-busy="false"]').waitFor();
  mode = 'empty'; await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await page.getByText('아직 집계할 SYNC ME 완료 기록이 없어요.', { exact: true }).waitFor();
  assert.equal(await page.getByText('0명', { exact: true }).count(), 3);
  assert.equal(await rows.locator('td.num').filter({ hasText: /^—$/ }).count(), 6);
  mode = 'no-visits'; await page.getByRole('button', { name: '새로고침', exact: true }).click(); await page.getByText('4명', { exact: true }).waitFor();
  assert.deepEqual(await rows.last().locator('td').allTextContents(), ['해당 동료와 상호 방문 형성', '0명', '—', '0.0%']);
  mode = 'error'; await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '전환 분석을 불러오지 못했어요.' }).waitFor();
  assert.equal(await page.getByText('0명', { exact: true }).count(), 0);
  assert.equal(await rows.locator('td.num').filter({ hasText: /^—$/ }).count(), 9);
  mode = 'normal'; await page.getByRole('button', { name: '새로고침', exact: true }).click(); await page.getByText('3명', { exact: true }).waitFor();
  record('refresh preserves data while loading; zero denominators, empty data and failed requests stay distinct and retry recovers');

  snapshot = (await page.content()).replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
  for (const width of [1440, 768, 430, 390, 360]) {
    await page.setViewportSize({ width, height: 1000 }); await page.waitForLoadState('networkidle');
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `conversion fits ${width}`);
    assert.equal(await page.locator('h1').count(), 1);
    assert.equal(await page.locator('.admin-sidebar nav [aria-current="page"]').count(), 1);
    for (const cell of await rows.locator('.num').all()) assert.equal(await cell.evaluate(element => getComputedStyle(element).textAlign), 'right');
    await page.screenshot({ path: resolve(output, `admin-discovery-conversion-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 1000 });
  await page.getByRole('button', { name: '메뉴', exact: true }).click();
  await page.getByRole('navigation', { name: '관리자 메뉴', exact: true }).getByRole('link', { name: '참여 현황', exact: true }).click();
  await page.getByRole('button', { name: '메뉴', exact: true }).click();
  await page.getByRole('navigation', { name: '관리자 메뉴', exact: true }).getByRole('link', { name: '동료 탐색 전환 분석', exact: true }).click();
  await page.getByText('3명', { exact: true }).waitFor();
  assert.equal(await page.getByRole('navigation', { name: '관리자 메뉴', exact: true }).isVisible(), false);
  record('existing admin navigation and right-aligned counts work at 360/390/430/768/1440 widths');

  mode = 'forbidden'; await page.getByRole('button', { name: '새로고침', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '관리자 권한이 필요해요.' }).waitFor();
  assert.equal(await page.getByText('3명', { exact: true }).count(), 0);
  const before = reads; allowed = false; await page.reload();
  await page.getByRole('alert').filter({ hasText: '관리자 권한이 필요해요.' }).waitFor();
  assert.equal(reads, before); assert.equal(await page.locator('.admin-conversion-panel').count(), 0);
  assert.deepEqual(mutations, ['/auth/login']); assert.deepEqual(errors, []);
  record('denied access clears aggregate values and never mounts analysis before server admin verification; no participant writes');
  await writeFile(resolve(output, 'discovery-conversion-checks.json'), JSON.stringify({ kind: 'synthetic browser API fixtures only', checks, mutations, errors }, null, 2));

  const inspector = resolve('../.taste-skill/discovery-conversion-render'); await mkdir(inspector, { recursive: true });
  const launcher = process.platform === 'win32' ? chromium.executablePath() : resolve(inspector, 'chromium.sh');
  if (process.platform !== 'win32') { await writeFile(launcher, `#!/usr/bin/env bash\nexec "${chromium.executablePath()}" --no-sandbox "$@"\n`); await chmod(launcher, 0o700); }
  await new Promise((done, reject) => {
    const child = spawn(process.execPath, ['scripts/taste.mjs', 'inspect', 'http://127.0.0.1:4576/discovery-conversion-snapshot.html', '--app', '--widths', '1440,768,390', '--out', '.taste-skill/discovery-conversion-render'], { cwd: resolve('..'), env: { ...process.env, CHROME_PATH: launcher }, stdio: 'inherit' });
    child.on('error', reject); child.on('exit', code => code === 0 ? done() : reject(new Error(`Conversion render check exited ${code}`)));
  });
  const measured = JSON.parse(await readFile(resolve(inspector, 'report.json'), 'utf8'));
  for (const viewport of measured.viewports) assert.equal(viewport.metrics.h1.text, '동료 탐색 전환 분석', 'inspector must measure the conversion page');
} finally { await browser?.close(); await new Promise(done => server.httpServer.close(done)); }
