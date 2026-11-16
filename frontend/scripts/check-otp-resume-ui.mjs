/* global document, window */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Explicit browser contracts only: no real accounts, OTP delivery or production endpoints.
const output = resolve('../output/playwright');
await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4275, strictPort: true } });
let browser;
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  await page.clock.install();
  let now = Date.now(), issued = 0, posts = 0, loseResponse = false, limitedUntil = 0, processing = false;
  await page.clock.pauseAt(new Date(now));
  const flows = new Map(), receipts = new Map(), errors = [], checks = [];
  page.on('pageerror', error => errors.push(error.message));
  const remaining = until => Math.max(0, Math.ceil((until - now) / 1000));
  const challenge = flow => ({ challengeId: flow.id, expiresIn: remaining(flow.expires), resendAfter: Math.max(remaining(flow.resend), remaining(limitedUntil)), reused: true });
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.slice(7);
    const reply = (status, json) => route.fulfill({ status, ...(status === 204 ? {} : { json }) });
    if (path === '/auth/config') return reply(200, { available: true, participantAvailable: true, passwordMinLength: 8, passwordMaxLength: 20 });
    if (path === '/users/me') return reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' });
    if (path === '/tags/pending') return reply(200, { present: false });
    if (path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'otp-resume-csrf' });
    if (path === '/auth/otp/pending') {
      const flow = flows.get(url.searchParams.get('purpose'));
      return reply(200, { username: flow?.username ?? null, challenge: flow && remaining(flow.expires) && !processing ? challenge(flow) : null,
        retryAfter: Math.max(remaining(flow?.resend ?? 0), remaining(limitedUntil)), limitCode: remaining(limitedUntil) ? 'OTP_RATE_LIMITED' : null,
        expired: !!flow && !remaining(flow.expires), inProgress: processing });
    }
    assert.equal(req.headers()['x-xsrf-token'], 'otp-resume-csrf');
    const body = req.postDataJSON();
    if (path === '/auth/otp/request') {
      posts++;
      assert.match(body.requestId, /^[A-Za-z0-9_-]{16,64}$/);
      const key = `${body.purpose}:${body.requestId}`, old = flows.get(body.purpose);
      if (receipts.has(key)) return reply(200, challenge(receipts.get(key)));
      if (!body.resend && old?.username === body.username && remaining(old.expires)) return reply(200, challenge(old));
      if (remaining(limitedUntil)) return reply(429, { code: 'OTP_RATE_LIMITED', message: '최근 5분간 요청 한도에 도달했어요.', retryAfter: remaining(limitedUntil) });
      const flow = { username: body.username, id: String.fromCharCode(65 + ++issued).repeat(43), expires: now + 180000, resend: now + 60000 };
      flows.set(body.purpose, flow); receipts.set(key, flow);
      if (loseResponse) { loseResponse = false; return route.abort('failed'); }
      return reply(200, { ...challenge(flow), reused: false });
    }
    throw new Error(`Unexpected OTP resume fixture request: ${path}`);
  });
  async function visit(path) { await page.goto(`http://127.0.0.1:4275${path}`); await page.locator('h1').waitFor(); }
  async function advance(ms) { now += ms; await page.clock.fastForward(ms); }
  const code = page.getByLabel('인증번호', { exact: true }), username = page.getByLabel('Mattermost 아이디', { exact: true });
  const send = () => page.getByRole('button', { name: '인증번호 받기', exact: true });
  const resend = () => page.getByRole('button', { name: /새 인증번호 받기/ });
  const record = name => { checks.push(name); console.log(`PASS ${name}`); };

  await visit('/auth/password-reset'); await username.fill('resume-fixture'); await send().click(); await code.waitFor();
  await code.fill('012345'); await page.getByLabel('새 비밀번호', { exact: true }).fill('Test-123');
  await visit('/auth'); await advance(20000); await visit('/auth/password-reset'); await code.waitFor();
  assert.equal(issued, 1); assert.equal(posts, 1);
  assert.equal(await username.inputValue(), 'resume-fixture'); assert.equal(await code.inputValue(), '');
  assert.equal(await page.getByLabel('새 비밀번호', { exact: true }).inputValue(), '');
  await page.getByText('진행 중인 인증을 이어서 할 수 있어요. 받은 번호를 입력해 주세요.', { exact: true }).waitFor();
  assert.equal(await resend().innerText(), '40초 후 새 인증번호 받기'); assert.equal(await resend().isEnabled(), false);
  for (const width of [360, 390, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
    await page.screenshot({ path: resolve(output, `otp-resume-${width}.png`), fullPage: true });
  }
  await page.reload(); await code.waitFor(); assert.equal(issued, 1);
  assert.equal(await resend().innerText(), '40초 후 새 인증번호 받기');
  record('navigation and reload restore the same OTP with original deadlines and clear password/code inputs at five widths');

  await advance(41000); assert.equal(await resend().isEnabled(), true);
  await page.getByRole('button', { name: '아이디 변경', exact: true }).click(); await send().click(); await code.waitFor();
  assert.equal(issued, 1, 'initial requests reuse the existing challenge after the resend interval too');
  await resend().click(); await page.getByText('발송 가능한 계정이면 Mattermost DM으로 번호가 도착해요. DM을 확인해 주세요.', { exact: true }).waitFor();
  assert.equal(issued, 2); assert.equal(await code.inputValue(), '');
  record('only the explicit new-number button issues another challenge; reentering the same account does not');

  await page.getByRole('button', { name: '아이디 변경', exact: true }).click(); await username.fill('lost-response-fixture');
  loseResponse = true; await send().click(); await page.getByRole('alert').waitFor();
  assert.equal(issued, 3); await send().click(); await code.waitFor();
  assert.equal(issued, 3, 'network retries reuse the same issuance request ID');
  record('a lost issuance response can be retried without a duplicate delivery');

  await advance(61000); limitedUntil = now + 130000; await resend().click();
  await page.getByRole('alert').filter({ hasText: '최근 5분간 요청 한도' }).waitFor();
  assert.equal(await resend().innerText(), '2분 10초 후 새 인증번호 받기');
  assert.equal(await code.isEnabled(), true);
  assert.equal(await page.getByRole('button', { name: '비밀번호 재설정하기', exact: true }).isEnabled(), true);
  flows.get('PASSWORD_RESET').expires = now - 1;
  await visit('/auth'); await visit('/auth/password-reset');
  const waiting = page.getByRole('button', { name: '2분 10초 후 다시 요청', exact: true });
  await waiting.waitFor(); assert.equal(await waiting.isEnabled(), false);
  assert.equal(await code.count(), 0);
  await page.screenshot({ path: resolve(output, 'otp-quota-account.png'), fullPage: true });
  await advance(60000); assert.equal(await page.getByRole('button', { name: '1분 10초 후 다시 요청', exact: true }).isEnabled(), false);
  await advance(71000); assert.equal(await send().isEnabled(), true);
  record('a five-minute quota shows the actual remaining wait in both steps without blocking existing-code verification or resetting on return');

  await visit('/auth/signup'); await username.waitFor(); assert.equal(await code.count(), 0); assert.equal(await username.inputValue(), '');
  assert.equal(issued, 3, 'changing purpose never silently sends another OTP');
  flows.set('PASSWORD_RESET', { username: 'processing-fixture', id: 'Z'.repeat(43), expires: now + 100000, resend: now + 20000 });
  processing = true; await visit('/auth/password-reset'); await page.getByRole('button', { name: '진행 중인 인증 확인 중…', exact: true }).waitFor();
  processing = false; await advance(1100); await code.waitFor(); assert.equal(await username.inputValue(), 'processing-fixture');
  assert.equal(issued, 3);
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
  record('purpose boundaries, in-flight recovery and empty browser credential storage are preserved');
  assert.deepEqual(errors, []);
  await writeFile(resolve(output, 'otp-resume-checks.json'), JSON.stringify({ kind: 'isolated browser fixtures; no real accounts or OTP delivery', checks, errors }, null, 2));
} finally { await browser?.close(); await server.httpServer.close(); }
