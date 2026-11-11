import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Browser/API contract only: no real credentials, DM, DB or official mission writes.
const server = await preview({ preview: { host: '127.0.0.1', port: 4274, strictPort: true } });
let browser;
try {
  browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 390, height: 900 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const missions = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  let signedIn = false, onboarded = true, pending = null, current = 0, resolves = 0, logins = 0, expireOnResolve = false;
  const tokenFor = i => String.fromCharCode(65 + i).repeat(43);
  const actionFor = () => ['프로필 완성하기','취향 알아보기','소원 남기기','탭 도전하기','응원 남기기'][current];
  const pathFor = i => missions[i] === 'SHOW_ME' ? '/me/profile' : `/missions/${missions[i].toLowerCase().replaceAll('_', '-')}`;
  let routeOverride = null;
  const user = () => ({ id: 1, username: 'contract-test', nickname: onboarded ? '테스트' : null, onboardingRequired: !onboarded });
  const profile = () => ({ identity: { realName: '테스트', region: '서울', classNo: 13, photoUrl: null, ready: true },
    profile: onboarded ? { nickname: '테스트', bio: '', interestIds: [], version: 1 } : null,
    consents: { ready: true, policies: ['PARTICIPATION', 'PROFILE_PUBLICATION'].map(type => ({ type, version: 'test-v1', title: '테스트 안내', body: '테스트 전용 동의', agreed: onboarded })) },
    profileComplete: onboarded, participationReady: onboarded });
  await context.route('**/api/v1/**', async route => {
    const request = route.request(), method = request.method(), path = new URL(request.url()).pathname.replace('/api/v1', '');
    const reply = (status, data) => route.fulfill({ status, contentType: 'application/json', body: [202, 204].includes(status) ? undefined : JSON.stringify(data) });
      if (method === 'POST' && path === '/missions/show-me/reconcile') { assert.ok(request.headers()['x-xsrf-token']); return reply(200, { newlyCompleted: false }); }
    try {
      if (method === 'GET') {
        if (path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
        if (path === '/users/me') return signedIn ? reply(200, user()) : reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' });
        if (path === '/tags/pending') return reply(200, { present: pending !== null });
        if (path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'test-csrf' });
        if (path === '/event') return reply(200, { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
        if (path === '/users/me/profile') return reply(200, profile());
        if (path === '/interests') return reply(200, []);
        if (path === '/missions/collection') return reply(200, { missions: missions.map((code, i) => ({ id: i + 1, code, active: true, state: 'LOCKED' })), completedCount: 0, completion: { complete: false } });
        if (path === '/discovery/graph') return reply(200, { self: { id: 1, nickname: '테스트' }, nodes: [], edges: [], truncated: false });
        // The destination mission's content is outside this STEP 1 contract.
        return reply(503, { code: 'TEST_OUT_OF_SCOPE', message: '미션 본문은 이 인증 테스트의 범위가 아닙니다.' });
      }
      assert.equal(request.headers()['x-xsrf-token'], 'test-csrf');
      const body = request.postDataJSON();
      if (path === '/tags/inspect') { const i = body.token ? current : pending; if(i === null)return reply(404,{code:'TAG_INTENT_EXPIRED',message:'태그 연결이 만료됐어요. 현장 태그를 다시 읽어주세요.'}); return reply(200,{missionCode:missions[i],letter:'SSAFY'[i],title:['SHOW ME','SYNC ME','A WISH COME TRUE','FAST TAP','YOU GOT THIS'][i]}); }
      if (path === '/tags/pending') { assert.equal(body.token, tokenFor(current)); pending = current; return reply(202); }
      if (path === '/auth/otp/request') { assert.equal(body.purpose, 'SIGN_UP'); return reply(200, { challengeId: 'Z'.repeat(43), expiresIn: 180, resendAfter: 60 }); }
      if (path === '/auth/login' || path === '/auth/signup') {
        logins++; signedIn = true;
        if (path.endsWith('signup')) { assert.equal(body.code, '012345'); onboarded = false; }
        return reply(path.endsWith('signup') ? 201 : 200, user());
      }
      if (path === '/users/me/profile') { onboarded = true; return reply(200, profile()); }
      if (path === '/auth/logout') { signedIn = false; pending = null; return reply(204); }
      if (path === '/tags/resume' || path === '/tags/resolve') {
        if (expireOnResolve) { expireOnResolve = false; signedIn = false; pending = null; }
        if (!signedIn) return reply(401, { code: 'SESSION_EXPIRED', message: '로그인이 만료됐어요. 다시 로그인해 주세요.' });
        if (path.endsWith('resume') && pending === null) return reply(404, { code: 'TAG_INTENT_EXPIRED', message: '태그 연결이 만료됐어요. 현장 태그를 다시 읽어주세요.' });
        const i = path.endsWith('resume') ? pending : current;
        if (path.endsWith('resolve')) assert.equal(body.token, tokenFor(current));
        pending = null; resolves++;
        return reply(200, { missionCode: missions[i], state: missions[i] === 'SHOW_ME' ? 'LOCKED' : 'UNLOCKED', route: routeOverride ?? pathFor(i), newlyUnlocked: missions[i] !== 'SHOW_ME', newlyCompleted: false });
      }
      throw new Error(`Unexpected mutation ${path}`);
    } catch (error) { errors.push(error.message); return reply(500, { message: '계약 검사 실패' }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4274${path}`);
  async function login() {
    await page.getByLabel('아이디', { exact: true }).fill('contract-test');
    await page.getByLabel('비밀번호', { exact: true }).fill('a long test password');
    await page.getByRole('button', { name: '로그인하기', exact: true }).click();
  }
  for (const mode of ['login', 'signup']) {
    for (current = 0; current < missions.length; current++) {
      signedIn = false; pending = null; onboarded = mode === 'login'; resolves = 0;
      await visit(`/t/${tokenFor(current)}`);
      await page.getByRole('button', { name: '로그인하고 이어가기', exact: true }).click();
      await page.waitForURL('**/auth');
      assert.equal(pending, current); assert.equal(resolves, 0);
      await page.reload(); // Only server-side pending intent survives this full reload.
      if (mode === 'login') await login();
      else {
        await page.getByRole('link', { name: '회원가입', exact: true }).click();
        await page.getByLabel('Mattermost 아이디', { exact: true }).fill('contract-test');
        await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
        await page.getByLabel('인증번호', { exact: true }).fill('012345');
        await page.getByLabel('비밀번호', { exact: true }).fill('a long test password');
        await page.getByLabel('비밀번호 확인', { exact: true }).fill('a long test password');
        await page.getByRole('button', { name: '가입하기', exact: true }).click();
        await page.waitForURL('**/me/profile');
        await page.reload();
        await page.getByLabel('닉네임', { exact: true }).fill('테스트');
        for (const box of await page.locator('input[name="policy"]').all()) await box.check();
        await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click();
      }
      await page.waitForURL('**/t/resume');
      await page.reload();
      await page.getByRole('button', { name: actionFor(), exact: true }).click();
      await page.waitForURL(`**${pathFor(current)}`);
      assert.equal(resolves, 1); assert.equal(pending, null);
      assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
      console.log(`PASS ${mode}: ${missions[current]} survives reload and returns to original mission`);
    }
  }
  current = 0;
  for (const width of [390,1440]) {
    await page.setViewportSize({ width, height: 900 });
    await visit(`/t/${tokenFor(current)}`);
    await page.getByRole('button', { name: actionFor(), exact: true }).click();
    await page.waitForURL('**/me/profile');
  }
  for (const [mission, invalidRoute] of [[0, '/missions/show-me'], [0, 'https://example.com'], [1, '/me/profile']]) {
    current = mission; routeOverride = invalidRoute;
    await visit(`/t/${tokenFor(current)}`);
    await page.getByRole('button', { name: actionFor(), exact: true }).click();
    await page.getByRole('alert').filter({ hasText: '이동할 미션을 확인하지 못했어요.' }).waitFor();
    assert.equal(new URL(page.url()).pathname, `/t/${tokenFor(current)}`);
  }
  routeOverride = null;
  console.log('PASS legacy SHOW ME direct links at 390/1440 and invalid/cross-mission routes rejected');
  current = 1; const before = logins;
  await visit(`/t/${tokenFor(current)}`);
  await page.reload();
  await page.getByRole('button', { name: actionFor(), exact: true }).click();
  await page.waitForURL(`**${pathFor(current)}`); assert.equal(logins, before);
  console.log('PASS valid session reconnect skips login');

  await visit(`/t/${tokenFor(current)}`); expireOnResolve = true;
  await page.getByRole('button', { name: actionFor(), exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '로그인이 만료' }).waitFor();
  await page.getByRole('button', { name: '로그인하고 이어가기', exact: true }).click();
  await page.waitForURL('**/auth'); await login(); await page.waitForURL('**/t/resume');
  await page.getByRole('button', { name: actionFor(), exact: true }).click();
  await page.waitForURL(`**${pathFor(current)}`);
  console.log('PASS expired session 401 recovers through login and original NFC');

  pending = null; await visit('/t/resume');
  await page.getByRole('alert').filter({ hasText: '태그 연결이 만료' }).waitFor();
  console.log('PASS expired pending intent asks for a new scan');
  await visit('/me'); await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.waitForURL('**/auth'); await visit(`/t/${tokenFor(current)}`);
  await page.getByRole('button', { name: '로그인하고 이어가기', exact: true }).waitFor();
  assert.equal(signedIn, false); assert.deepEqual(errors, []);
  console.log('PASS logout requires login on next NFC entry');
} finally {
  await browser?.close();
  await new Promise(done => server.httpServer.close(done));
}
