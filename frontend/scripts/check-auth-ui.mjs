/* global document, window */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Browser/API contract tests only. This intercepts requests in the test browser;
// it is never imported by the app and creates no real accounts or DM messages.
const output = resolve('../output/playwright');
await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4273, strictPort: true } });
let browser;
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const checks = [], pageErrors = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  let user = null, otpCount = 0, signupCount = 0, changeCount = 0, resetCount = 0;
  let lastOtp = null, lastSignup = null, lastChange = null;
  let limited = false, unavailable = false;
  let releaseOtp, heldOtp = null;
  const sample = { id: 1, username: 'contract-test', nickname: null, onboardingRequired: true };
  const password = 'first test password';
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    const method = request.method();
    const reply = (status, data, headers = {}) => route.fulfill({ status, contentType: 'application/json', headers, body: status === 204 ? undefined : JSON.stringify(data) });
    if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
    if (method === 'GET' && path === '/users/me') return user ? reply(200, user) : reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' });
    if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'test-csrf-token' });
    assert.equal(request.headers()['x-xsrf-token'], 'test-csrf-token', 'every mutation includes CSRF');
    const body = request.postDataJSON();
    if (path === '/auth/otp/request') {
      otpCount++; lastOtp = body;
      if (heldOtp) await heldOtp;
      if (limited) return reply(429, { code: 'RATE_LIMITED', message: '요청이 많아요. 잠시 기다려 주세요.', retryAfter: 2 }, { 'Retry-After': '2' });
      if (unavailable) return reply(503, { code: 'AUTH_UNAVAILABLE', message: '인증 서비스를 사용할 수 없어요.' });
      return reply(200, { challengeId: String.fromCharCode(96 + otpCount).repeat(43), expiresIn: 180, resendAfter: 60 });
    }
    if (path === '/auth/signup') { signupCount++; lastSignup = body; user = sample; return reply(201, user); }
    if (path === '/auth/login') {
      assert.deepEqual(Object.keys(body).sort(), ['password', 'username']); user = sample; return reply(200, user);
    }
    if (path === '/users/me/password') { assert.equal(method, 'PUT'); changeCount++; lastChange = body; user = null; return reply(204); }
    if (path === '/auth/password/reset') { resetCount++; user = null; return reply(204); }
    if (path === '/auth/logout') { user = null; return reply(204); }
    throw new Error(`Unexpected contract request ${method} ${path}`);
  });
  async function visit(path) {
    await page.goto(`http://127.0.0.1:4273${path}`);
    await page.locator('.form-boundary').filter({ hasText: '아이디는 Mattermost와 같아요.' }).waitFor();
  }
  async function record(name) { checks.push(name); console.log(`PASS ${name}`); }
  await page.clock.install();

  await visit('/auth/signup');
  await page.getByLabel('Mattermost 아이디', { exact: true }).fill('contract-test');
  heldOtp = new Promise((done) => { releaseOtp = done; });
  await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
  await page.locator('form[aria-busy="true"]').waitFor();
  assert.equal(await page.getByRole('button', { name: '처리 중…', exact: true }).isEnabled(), false);
  releaseOtp(); heldOtp = null;
  await page.getByLabel('인증번호', { exact: true }).waitFor();
  assert.equal(otpCount, 1);
  assert.deepEqual(lastOtp, { purpose: 'SIGN_UP', username: 'contract-test' });
  assert.equal(await page.getByRole('button', { name: /초 후 다시 받기/ }).isEnabled(), false);
  await page.getByLabel('인증번호', { exact: true }).fill('012345');
  await page.clock.fastForward(61_000);
  await page.getByRole('button', { name: '인증번호 다시 받기', exact: true }).click();
  await page.locator('form[aria-busy="false"]').waitFor();
  assert.equal(await page.getByLabel('인증번호', { exact: true }).inputValue(), '');
  await page.getByLabel('인증번호', { exact: true }).fill('012345');
  await page.getByLabel('비밀번호', { exact: true }).fill('short');
  await page.getByLabel('비밀번호 확인', { exact: true }).fill('short');
  await page.getByRole('button', { name: '가입하기', exact: true }).click();
  assert.equal(signupCount, 0);
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  await page.getByLabel('비밀번호 확인', { exact: true }).fill(password);
  for (const width of [360, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
    await page.screenshot({ path: resolve(output, `auth-contract-verify-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 900 });
  await page.getByRole('button', { name: '가입하기', exact: true }).click();
  await page.getByRole('heading', { name: 'contract-test님, 반가워요', exact: true }).waitFor();
  assert.equal(lastSignup.code, '012345');
  assert.equal(lastSignup.password, password);
  assert.equal(lastSignup.challengeId, 'b'.repeat(43), 'resend replaces the challenge ID');
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
  await page.screenshot({ path: resolve(output, 'auth-contract-account-390.png'), fullPage: true });
  await record('signup contract, busy submission, resend replacement, numeric code and password policy');

  await page.getByRole('link', { name: '비밀번호 변경', exact: true }).click();
  await page.getByRole('heading', { name: '비밀번호 변경', exact: true }).waitFor();
  assert.equal(await page.locator('input[name="username"]').count(), 0);
  await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
  await page.getByLabel('인증번호', { exact: true }).waitFor();
  assert.deepEqual(lastOtp, { purpose: 'PASSWORD_CHANGE' });
  await page.getByLabel('인증번호', { exact: true }).fill('012345');
  await page.getByLabel('새 비밀번호', { exact: true }).fill('replacement test password');
  await page.getByLabel('새 비밀번호 확인', { exact: true }).fill('replacement test password');
  await page.getByRole('button', { name: '비밀번호 변경하기', exact: true }).click();
  await page.getByRole('heading', { name: '로그인', exact: true }).waitFor();
  assert.equal(changeCount, 1);
  assert.deepEqual(Object.keys(lastChange).sort(), ['challengeId', 'code', 'newPassword']);
  assert.ok((await page.getByRole('status').innerText()).includes('모든 기존 세션'));
  assert.equal(await page.getByLabel('비밀번호', { exact: true }).inputValue(), '');
  await record('password change uses the current session and returns to login with cleared secrets');

  await page.getByLabel('Mattermost 아이디', { exact: true }).fill('contract-test');
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  assert.equal(await page.locator('input[name="code"]').count(), 0);
  await page.getByRole('button', { name: '로그인하기', exact: true }).click();
  await page.getByRole('heading', { name: 'contract-test님, 반가워요', exact: true }).waitFor();
  await page.reload();
  await page.getByRole('heading', { name: 'contract-test님, 반가워요', exact: true }).waitFor();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.getByRole('heading', { name: '로그인', exact: true }).waitFor();
  await record('ordinary login has no OTP and refresh restores server session; logout clears it');

  await page.getByRole('link', { name: '비밀번호를 잊으셨나요?', exact: true }).click();
  await page.getByRole('heading', { name: '비밀번호 재설정', exact: true }).waitFor();
  await page.getByLabel('Mattermost 아이디', { exact: true }).fill('contract-test');
  await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
  await page.getByLabel('인증번호', { exact: true }).waitFor();
  assert.deepEqual(lastOtp, { purpose: 'PASSWORD_RESET', username: 'contract-test' });
  await page.getByLabel('인증번호', { exact: true }).fill('012345');
  await page.getByLabel('새 비밀번호', { exact: true }).fill(password);
  await page.getByLabel('새 비밀번호 확인', { exact: true }).fill(password);
  await page.getByRole('button', { name: '비밀번호 재설정하기', exact: true }).click();
  await page.getByRole('heading', { name: '로그인', exact: true }).waitFor();
  assert.equal(resetCount, 1); assert.equal(user, null);
  await record('reset uses its own purpose and never automatically logs in');

  await visit('/auth/signup');
  await page.getByLabel('Mattermost 아이디', { exact: true }).fill('contract-test');
  limited = true;
  await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
  await page.getByRole('status').filter({ hasText: '요청이 많아요' }).waitFor();
  assert.equal(await page.getByRole('button', { name: '인증번호 받기', exact: true }).isEnabled(), false);
  await page.clock.fastForward(3_000); limited = false; unavailable = true;
  await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
  await page.getByRole('status').filter({ hasText: '인증 서비스를 사용할 수 없어요' }).waitFor();
  assert.equal(await page.locator('input[name="code"]').count(), 0);
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
  assert.ok(!new URL(page.url()).search.includes('password'));
  await record('429 cooldown and upstream failure retain the account step without fake DM success');
  assert.deepEqual(pageErrors, []);
  await writeFile(resolve(output, 'auth-contract-checks.json'), JSON.stringify({ kind: 'browser contract mock; no real DM/account creation', passed: checks, pageErrors }, null, 2));
} finally {
  await browser?.close();
  await new Promise((done) => server.httpServer.close(done));
}
