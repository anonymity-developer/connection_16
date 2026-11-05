/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir, writeFile, chmod } from 'node:fs/promises';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Explicit API contract fixtures, never imported by the application.
const output = resolve('../output/playwright');
await mkdir(output, { recursive: true });
const catalog = [{ id: 1, name: '백엔드', category: 'DOMAIN' }, { id: 2, name: 'AI', category: 'DOMAIN' }, { id: 3, name: '클라우드', category: 'DOMAIN' }, { id: 4, name: 'React', category: 'TECH_STACK' }, ...['프론트엔드', '데이터', '게임', '디자인'].map((name, i) => ({ id: i + 5, name, category: 'DOMAIN' })), ...['Java', 'Spring Boot', 'Python', 'FastAPI', 'Django', 'JavaScript', 'TypeScript', 'Vue', 'Next.js', 'MySQL', 'PostgreSQL', 'Redis', 'Docker', 'Kubernetes', 'AWS', 'Flutter', 'Kotlin', 'Swift', 'C++', 'PyTorch'].map((name, i) => ({ id: i + 9, name, category: 'TECH_STACK' }))];
const renderFixture = path => ({
  '/auth/config': { available: true, passwordMinLength: 15, passwordMaxLength: 64 },
  '/users/me': { id: 1, username: 'profile-render-test', nickname: null, onboardingRequired: true },
  '/tags/pending': { present: false },
  '/event': { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false },
  '/interests': catalog,
  '/missions/collection': { missions: ['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'].map((code, i) => ({ id: i + 1, code, state: 'LOCKED', active: true })), completedCount: 0, completion: { complete: false, completedAt: null, completionOrder: null, awardConfirmed: false } },
  '/users/me/profile': { identity: { realName: '테스트이름', region: '서울', classNo: 13, photoUrl: null, ready: true }, profile: null, consents: { ready: true, policies: ['PARTICIPATION', 'PROFILE_PUBLICATION'].map(type => ({ type, version: 'test-v1', title: type === 'PARTICIPATION' ? '참여 안내 (테스트)' : '프로필 공개 안내 (테스트)', body: '검사 전용 안내입니다. 실제 운영 정책이 아닙니다.', agreed: false })) }, profileComplete: false, participationReady: false },
})[path];
// Read-only, loopback-only fixtures let the rendered-page inspector see the actual profile form.
// This middleware belongs only to this test preview; no app or production configuration imports it.
const server = await preview({ preview: { host: '127.0.0.1', port: 4373, strictPort: true }, plugins: [{ name: 'profile-read-fixture', configurePreviewServer(server) { server.middlewares.use('/api/v1', (req, res) => { const value = renderFixture(new URL(req.url, 'http://127.0.0.1').pathname); res.setHeader('Content-Type', 'application/json'); res.statusCode = req.method !== 'GET' ? 405 : value === undefined ? 404 : 200; res.end(JSON.stringify(value ?? {})); }); } }] });
let browser;
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const checks = [], pageErrors = [], routeErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  let signedIn = true, pending = false, unlocked = false, completed = false, ready = true, closed = false, collectionFailure = false, profileConflict = false, sourceReady = true, photoFails = false;
  let profile = null, saves = 0, resolves = 0, agreements = false, heldSave = null, releaseSave;
  const token = 'A'.repeat(43);
  const policies = () => ['PARTICIPATION', 'PROFILE_PUBLICATION'].map((type, index) => ({ type, version: 'test-v1', title: index ? '프로필 공개 안내 (테스트)' : '참여 안내 (테스트)', body: '브라우저 검사 전용 동의 문구입니다. 실제 운영 정책이 아닙니다.\n닉네임을 누르면 전체 정보가 공개되는 테스트입니다.', agreed: agreements }));
  const state = () => ({ identity: { realName: '테스트이름', region: '서울', classNo: 13, photoUrl: '/api/v1/users/me/photo?v=7', ready: sourceReady }, profile, consents: { ready, policies: ready ? policies() : [] }, profileComplete: !!profile, participationReady: !!profile && agreements });
  const user = () => ({ id: 1, username: 'contract-test', nickname: profile?.nickname ?? null, onboardingRequired: !state().participationReady });
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(); const path = new URL(req.url()).pathname.replace('/api/v1', ''); const method = req.method();
      const reply = (status, data) => route.fulfill({ status, contentType: 'application/json', body: [202, 204].includes(status) ? undefined : JSON.stringify(data) });
      if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
      if (method === 'GET' && path === '/discovery/graph') return reply(200, { self: { id: 1, nickname: '테스트' }, nodes: [], edges: [], truncated: false });
      if (method === 'GET' && path === '/users/me') return signedIn ? reply(200, user()) : reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' });
      if (method === 'GET' && path === '/tags/pending') return reply(200, { present: pending });
      if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'contract-csrf' });
      if (method === 'GET' && path === '/event') return reply(200, { status: closed ? 'CLOSED' : 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
      if (method === 'GET' && path === '/interests') return reply(200, catalog);
      if (method === 'GET' && path === '/users/me/photo') return photoFails ? reply(503, { code: 'UNAVAILABLE', message: '사진을 불러오지 못했어요.' }) : route.fulfill({ status: 200, contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aENcAAAAASUVORK5CYII=', 'base64') });
      if (method === 'GET' && path === '/users/me/profile') return reply(200, state());
      if (method === 'GET' && path === '/missions/collection') {
        if (collectionFailure) return reply(503, { code: 'UNAVAILABLE', message: '참여 기록을 불러오지 못했어요.' });
        return reply(200, { missions: ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'].map((code, i) => ({ id: i + 1, code, active: true, state: i === 0 ? completed ? 'COMPLETED' : unlocked ? 'UNLOCKED' : 'LOCKED' : 'LOCKED', unlockedAt: null, completedAt: null })), completedCount: completed ? 1 : 0, completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } });
      }
      assert.equal(req.headers()['x-xsrf-token'], 'contract-csrf');
      const body = req.postDataJSON();
      if (path === '/auth/login') { signedIn = true; return reply(200, user()); }
      if (path === '/tags/pending') { assert.equal(body.token, token); pending = true; return reply(202); }
      if (path === '/tags/resume' || path === '/tags/resolve') {
        resolves++;
        if (path === '/tags/resume' && !pending) return reply(404, { code: 'TAG_INTENT_EXPIRED', message: '태그 연결이 만료됐어요. 현장 태그를 다시 읽어주세요.' });
        if (closed) return reply(403, { code: 'EVENT_CLOSED', message: '지금은 참여 가능한 시간이 아니에요.' });
        const newlyUnlocked = !unlocked, newlyCompleted = !completed && state().participationReady;
        unlocked = true; if (state().participationReady) completed = true; pending = false;
        return reply(200, { missionCode: 'SHOW_ME', state: completed ? 'COMPLETED' : 'UNLOCKED', route: '/missions/show-me', newlyUnlocked, newlyCompleted });
      }
      if (path === '/users/me/profile' && method === 'PUT') {
        saves++; if (heldSave) await heldSave;
        if (profileConflict) return reply(409, { code: 'PROFILE_CHANGED', message: '프로필이 변경됐어요. 새로 불러온 뒤 다시 저장해 주세요.' });
        for (const field of ['realName','region','classNo','isPublic','imageUrl']) assert.equal(field in body, false);
        assert.equal('userId' in body, false); assert.equal('completed' in body, false);
        assert.equal(body.expectedVersion, profile?.version ?? 0);
        assert.deepEqual(body.acceptPolicies.map(p => p.type).sort(), agreements ? [] : ['PARTICIPATION', 'PROFILE_PUBLICATION']);
        agreements = true; profile = { ...body, version: (profile?.version ?? 0) + 1 };
        if (unlocked && profile) completed = true;
        return reply(200, state());
      }

      throw new Error(`Unexpected contract request ${method} ${path}`);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4373${path}`);
  const record = name => { checks.push(name); console.log(`PASS ${name}`); };
  async function fill() {
    await page.getByLabel('닉네임', { exact: true }).fill('테스트별명');
    await page.getByLabel('한 줄 소개 (선택)', { exact: true }).fill('함께 배우고 만드는 걸 좋아해요.');
    await page.getByRole('checkbox', { name: '백엔드', exact: true }).check(); await page.getByRole('checkbox', { name: 'React', exact: true }).check();
    const extra = page.locator('input[name="interest"]:not(:checked)');
    for (let i = 0; i < 8; i++) await extra.first().check();
    assert.equal(await extra.first().isDisabled(), true);
    while (await page.locator('input[name="interest"]:checked').count()) await page.locator('input[name="interest"]:checked').first().uncheck();
    await page.getByRole('checkbox', { name: '백엔드', exact: true }).check(); await page.getByRole('checkbox', { name: 'React', exact: true }).check();
  }
  async function agree() { for (const box of await page.locator('input[name="policy"]').all()) await box.check(); }
  await visit('/me/profile'); await page.getByLabel('닉네임', { exact: true }).waitFor();
  assert.equal(await page.locator('input[name="policy"]').count(), 1);
  assert.equal(await page.locator('input[name="realName"],input[name="classNo"]').count(), 0);
  assert.equal(await page.locator('input[name="policy"]:checked').count(), 0);
  await fill(); await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click(); assert.equal(saves, 0);
  await page.getByRole('button', { name: /내용 보기$/ }).first().click();
  await page.getByRole('dialog').waitFor(); assert.ok((await page.getByRole('dialog').innerText()).includes('실제 운영 정책이 아닙니다.'));
  await page.keyboard.press('Escape'); await agree();
  for (const width of [360, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false);
    for (const heading of await page.locator('h1,h2').all()) {
      assert.ok(await heading.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1));
    }
    await page.screenshot({ path: resolve(output, `participation-profile-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 900 });
  heldSave = new Promise(done => { releaseSave = done; });
  await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click();
  await page.locator('form[aria-busy="true"]').waitFor(); assert.equal(await page.getByRole('button', { name: '저장 중…', exact: true }).isEnabled(), false);
  releaseSave(); heldSave = null; await page.waitForURL('**/me');
  assert.equal(saves, 1); assert.equal(completed, false);
  await visit('/'); await page.getByRole('progressbar', { name: '나의 글자 수집' }).waitFor();
  assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'), '0');
  assert.equal(await page.locator('input[name="realName"],input[name="classNo"]').count(), 0);
  record('profile imports readonly identity, groups domain and stack interests, requires one acknowledgement and awards no letter before the tag');

  await visit(`/t/${token}`); await page.getByRole('button', { name: '태그로 미션 열기', exact: true }).click(); await page.waitForURL('**/missions/show-me');
  await page.getByRole('heading', { name: '연결의 한 조각을 모았어요', exact: true }).waitFor();
  await page.screenshot({ path: resolve(output, 'participation-show-me-complete-390.png'), fullPage: true });
  await page.getByRole('link', { name: '수집판으로 돌아가기', exact: true }).click();
  await page.getByRole('progressbar').waitFor(); assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'), '1');
  await page.reload(); await page.getByRole('progressbar').waitFor(); assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'), '1');
  assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuemax'), '5');
  await page.screenshot({ path: resolve(output, 'participation-collection-390.png'), fullPage: true });
  record('SHOW ME tag uses server result and collection keeps exactly one of five letters after refresh');

  signedIn = false; profile = null; agreements = false; unlocked = false; completed = false;
  await visit(`/t/${token}`); await page.getByRole('button', { name: '로그인하고 이어가기', exact: true }).click(); await page.waitForURL('**/auth');
  assert.equal(pending, true); assert.equal(unlocked, false); await page.reload();
  await page.getByLabel('Mattermost 아이디', { exact: true }).fill('contract-test'); await page.getByLabel('비밀번호', { exact: true }).fill('a long test password');
  await page.getByRole('button', { name: '로그인하기', exact: true }).click(); await page.waitForURL('**/me');
  await page.getByRole('link', { name: '프로필 등록하기', exact: true }).click(); await fill(); await agree();
  await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click(); await page.waitForURL('**/t/resume');
  await page.reload(); await page.getByRole('button', { name: '태그로 미션 열기', exact: true }).click(); await page.waitForURL('**/missions/show-me');
  assert.equal(completed, true); assert.equal(pending, false);
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
  record('anonymous tag intent survives login reload and onboarding without browser credential or token storage');

  await visit('/me/profile'); await page.getByLabel('닉네임', { exact: true }).waitFor(); profileConflict = true;
  await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click(); await page.getByRole('alert').waitFor();
  assert.equal(await page.getByRole('button', { name: '프로필 저장하기', exact: true }).isEnabled(), false);
  await page.getByRole('button', { name: '최신 프로필과 동의 불러오기', exact: true }).click(); profileConflict = false;
  await page.getByRole('button', { name: '프로필 저장하기', exact: true }).waitFor();
  closed = true; await page.reload(); await page.getByRole('button', { name: '프로필 저장하기', exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: '프로필 저장하기', exact: true }).isEnabled(), false);
  assert.equal(await page.getByRole('button', { name: /비공개/ }).count(), 0);assert.equal(await page.getByRole('checkbox', { name: /내 프로필을 참여자에게 공개하기/ }).count(), 0);assert.equal(completed, true);
  record('stale profile saves require reload and closure never restores a removed private-profile control');
  closed = false; profile = null; agreements = false; unlocked = false; completed = false; photoFails = true;
  await visit('/me/profile'); await page.getByLabel('닉네임', { exact: true }).fill('닉네임만'); await agree(); await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click(); await page.waitForURL('**/me'); assert.deepEqual(profile.interestIds, []);assert.equal(profile.bio, null);assert.equal(state().participationReady, true);
  sourceReady = false; await visit('/me/profile'); await page.getByRole('heading', { name: 'Mattermost 정보를 다시 확인해 주세요', exact: true }).waitFor();assert.equal(await page.locator('input[name="nickname"]').count(), 0);sourceReady = true;
  record('nickname-only onboarding accepts empty optional fields and unavailable photos; missing source identity requires login rather than manual names');

  pending = false; await visit('/t/resume'); await page.getByRole('button', { name: '태그로 미션 열기', exact: true }).click();
  await page.getByRole('alert').waitFor(); assert.ok((await page.getByRole('alert').innerText()).includes('만료'));
  ready = false; await visit('/me/profile'); await page.getByRole('heading', { name: '참여 안내를 준비하고 있어요', exact: true }).waitFor();
  assert.equal(await page.locator('input[name="nickname"]').count(), 0);
  collectionFailure = true; await visit('/'); await page.getByRole('button', { name: '기록 다시 불러오기', exact: true }).waitFor();
  assert.equal(await page.getByRole('progressbar').count(), 0);
  assert.equal(await page.locator('meta[name="referrer"]').getAttribute('content'), 'no-referrer');
  record('expired tag, unconfigured policies and collection failure never advance or display fake counts');
  assert.deepEqual(pageErrors, []); assert.deepEqual(routeErrors, []); assert.ok(resolves >= 3);
  await writeFile(resolve(output, 'participation-contract-checks.json'), JSON.stringify({ kind: 'browser API contract fixtures; no real participants, policies or event tags', checks, pageErrors, routeErrors }, null, 2));
  const inspector = resolve('../.taste-skill/profile-render'); await mkdir(inspector, { recursive: true }); const launcher = resolve(inspector, 'chromium.sh'); await writeFile(launcher, `#!/usr/bin/env bash\nexec "${chromium.executablePath()}" --no-sandbox "$@"\n`); await chmod(launcher, 0o700);
  await new Promise((done, reject) => { const child = spawn(process.execPath, ['scripts/taste.mjs', 'inspect', 'http://127.0.0.1:4373/me/profile', '--app', '--widths', '1440,768,390', '--out', '.taste-skill/profile-render'], { cwd: resolve('..'), env: { ...process.env, CHROME_PATH: launcher }, stdio: 'inherit' }); child.on('error', reject); child.on('exit', code => code === 0 ? done() : reject(new Error(`Profile render check exited ${code}`))); });

} finally { await browser?.close(); await server.httpServer.close(); }
