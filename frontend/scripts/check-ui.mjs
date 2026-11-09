/* global document, window */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { createServer, preview } from 'vite';

// Run after `npm run build`. Both servers are local and fixtures are only
// exercised against Vite's development server, never the production artifact.
const output = resolve('../output/playwright');
await mkdir(output, { recursive: true });
let production, development, browser;
const results = [];
try {
  production = await preview({ preview: { host: '127.0.0.1', port: 4173, strictPort: true } });
  // preview() defaults NODE_ENV to production. The separate fixture server
  // must explicitly use development; the built production assets stay unchanged.
  process.env.NODE_ENV = 'development';
  development = await createServer({ server: { host: '127.0.0.1', port: 5174, strictPort: true } });
  await development.listen();
  browser = await chromium.launch();
  const context = await browser.newContext({ permissions: ['clipboard-read', 'clipboard-write'] });
  const page = await context.newPage();
  const failures = [];
  const writes = [];
  page.on('pageerror', (error) => failures.push(error.message));
  page.on('request', (request) => { if (request.method() !== 'GET') writes.push(request.url()); });
  const productionUrl = 'http://127.0.0.1:4173';
  const developmentUrl = 'http://127.0.0.1:5174';

  async function check(name, fn) { await fn(); results.push(name); console.log(`PASS ${name}`); }
  async function visit(url) {
    await page.goto(url);
    await page.locator('main h1').waitFor();
    await page.evaluate(() => document.fonts.ready);
  }
  async function visibleCount(selector) {
    return page.locator(selector).evaluateAll((elements) => elements.filter((element) => element.getClientRects().length > 0).length);
  }

  await check('production layouts, fonts and headings at 360/390/430/768/1440', async () => {
    for (const width of [360, 390, 430, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      for (const [name, path] of [['home', '/'], ['login', '/auth'], ['signup', '/auth/signup'], ['wish-locked', '/missions/make-a-wish'], ['discovery', '/discovery']]) {
        await visit(productionUrl + path);
        assert.equal(await visibleCount('main h1'), 1, `${name}: one visible page heading`);
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `${name} at ${width}: horizontal overflow`);
        assert.ok(await page.evaluate(() => [...document.fonts].some((font) => font.family.replace(/['"]/g, '') === 'DM Sans' && font.status === 'loaded')), 'self-hosted DM Sans loaded');
        await page.screenshot({ path: resolve(output, `${name}-${width}.png`), fullPage: true });
        if (width === 390) await page.screenshot({ path: resolve(output, `${name}-viewport-390.png`) });
      }
    }
  });
  await check('production ignores fixture state and guards password/complete routes', async () => {
    await visit(`${productionUrl}/?preview=complete`);
    assert.equal(await page.getByRole('complementary', { name: '개발용 상태 미리보기' }).count(), 0);
    assert.equal(await page.getByRole('progressbar').count(), 0);
    assert.equal(await page.locator('.collection-count').count(), 0, 'signed-out visitors do not see an invented collection count');
    assert.equal(await page.locator('.letter-slot').count(), 5, 'all five mission letters remain visible');
    assert.equal(await page.locator('.letter-slot.is-collected').count(), 0, 'signed-out visitors do not see falsely earned letters');
    await visit(`${productionUrl}/me/password?preview=unlocked`);
    assert.equal(await page.locator('input[name="password"]').count(), 0);
    await visit(`${productionUrl}/complete?preview=complete`);
    assert.equal(await page.locator('h1').innerText(), '페이지를 찾을 수 없어요');
    await visit(`${productionUrl}/t/${'a'.repeat(43)}`);
    assert.ok((await page.locator('main').innerText()).includes('로그인하고 태그로 이어가요'));
  });
  await check('login validation, password visibility, navigation and credential isolation', async () => {
    await visit(`${productionUrl}/auth`);
    await page.getByRole('link', { name: 'TAG ME 홈', exact: true }).focus();
    await page.keyboard.press('Shift+Tab');
    assert.equal(await page.getByRole('link', { name: '본문으로 바로가기', exact: true }).evaluate((element) => element === document.activeElement), true);
    await page.keyboard.press('Enter');
    assert.equal(await page.evaluate(() => document.activeElement?.id), 'main-content');
    await page.getByRole('button', { name: '로그인하기', exact: true }).click();
    assert.equal(await page.locator('input[name="username"]').evaluate((element) => document.activeElement === element), true);
    await page.getByLabel('Mattermost 아이디', { exact: true }).fill('ui_test');
    await page.getByLabel('비밀번호', { exact: true }).fill('example-only-password');
    await page.getByRole('button', { name: '비밀번호 표시', exact: true }).click();
    assert.equal(await page.getByLabel('비밀번호', { exact: true }).getAttribute('type'), 'text');
    await page.getByRole('button', { name: '비밀번호 숨기기', exact: true }).click();
    assert.equal(await page.getByLabel('비밀번호', { exact: true }).getAttribute('type'), 'password');
    await page.getByRole('button', { name: '로그인하기', exact: true }).click();
    assert.ok((await page.getByRole('status').innerText()).includes('전송되지 않았어요'));
    await page.getByRole('link', { name: '비밀번호를 잊으셨나요?' }).click();
    await page.getByRole('heading', { name: '비밀번호 재설정', exact: true }).waitFor();
    await page.goBack();
    await page.getByRole('heading', { name: '로그인', exact: true }).waitFor();
    assert.equal(await page.locator('input[name="password"]').inputValue(), '');
    await page.goForward();
    await page.getByRole('heading', { name: '비밀번호 재설정', exact: true }).waitFor();
    assert.equal(writes.length, 0, 'no credentials or other data submitted');
    assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0, 'no credentials/session stored');
  });
  await page.setViewportSize({ width: 390, height: 900 });
  await check('preview mode switching and browser back/forward', async () => {
    await visit(`${developmentUrl}/?preview=locked`);
    const bar = page.getByRole('complementary', { name: '개발용 상태 미리보기' });
    await bar.waitFor();
    await bar.getByRole('link', { name: 'pending', exact: true }).click();
    await page.waitForURL('**/?preview=pending');
    await page.getByRole('progressbar').filter({ visible: true }).waitFor();
    await page.waitForFunction(() => document.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow') === '2');
    await bar.getByRole('link', { name: 'complete', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow') === '5');
    await page.goBack();
    await page.waitForFunction(() => document.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow') === '2');
    await page.goForward();
    await page.waitForFunction(() => document.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow') === '5');
    assert.ok((await page.locator('.next-panel a').getAttribute('href')).startsWith('/complete'));
    await page.screenshot({ path: resolve(output, 'home-preview-complete-390.png'), fullPage: true });
    await page.getByRole('link', { name: '완성 화면 보기', exact: true }).click();
    await page.getByRole('heading', { name: '다섯 글자를 모두 모았어요', exact: true }).waitFor();
  });
  await check('leading-zero OTP paste, mismatch focus, dialog Escape/focus and account change', async () => {
    await visit(`${developmentUrl}/auth/signup?preview=unlocked`);
    await page.getByRole('complementary', { name: '개발용 상태 미리보기' }).waitFor();
    await page.getByLabel('Mattermost 아이디', { exact: true }).fill('ui_test');
    await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
    const code = page.getByLabel('인증번호', { exact: true });
    await code.waitFor();
    assert.equal(await code.getAttribute('type'), 'text');
    await page.evaluate(() => navigator.clipboard.writeText('012345'));
    await code.focus();
    await page.keyboard.press('Control+V');
    assert.equal(await code.inputValue(), '012345');
    await page.getByLabel('비밀번호', { exact: true }).fill('example-only-password');
    await page.getByLabel('비밀번호 확인', { exact: true }).fill('mismatch');
    await page.getByRole('button', { name: '가입하기', exact: true }).click();
    assert.equal(await page.getByLabel('비밀번호 확인', { exact: true }).evaluate((element) => element === document.activeElement), true);
    await page.getByLabel('비밀번호 확인', { exact: true }).fill('example-only-password');
    const submit = page.getByRole('button', { name: '가입하기', exact: true });
    await submit.click();
    await page.getByRole('dialog').waitFor();
    for (const key of ['Tab', 'Shift+Tab']) {
      for (let index = 0; index < 5; index++) {
        await page.keyboard.press(key);
        assert.equal(await page.evaluate(() => !!document.activeElement?.closest('dialog')), true);
      }
    }
    await page.keyboard.press('Escape');
    assert.equal(await page.getByRole('dialog').isVisible(), false);
    assert.equal(await submit.evaluate((element) => element === document.activeElement), true);
    await page.getByRole('button', { name: '아이디 변경', exact: true }).click();
    assert.equal(await page.locator('input[name="code"]').count(), 0);
    assert.equal(await page.locator('input[name="password"]').count(), 0);
    await page.getByRole('button', { name: '인증번호 받기', exact: true }).click();
    assert.equal(await code.inputValue(), '');
    assert.equal(await page.getByLabel('비밀번호', { exact: true }).inputValue(), '');
    await page.screenshot({ path: resolve(output, 'signup-verify-390.png'), fullPage: true });
  });
  await check('wish preview validation and local-only confirmation', async () => {
    await visit(`${developmentUrl}/missions/make-a-wish?preview=unlocked`);
    const submit = page.getByRole('button', { name: '소원 띄우기 미리보기', exact: true });
    await submit.waitFor();
    await submit.click();
    await page.getByRole('alert').waitFor();
    await page.getByLabel('지금, 어떤 소원을 품고 있나요?').fill('화면 검증용 문구');
    await submit.click();
    await page.getByRole('dialog').waitFor();
    await page.screenshot({ path: resolve(output, 'wish-dialog-390.png'), fullPage: true });
    await page.keyboard.press('Escape');
    assert.equal(await submit.evaluate((element) => element === document.activeElement), true);
    assert.equal(writes.length, 0);
  });
  assert.deepEqual(failures, [], 'browser page errors');
  await writeFile(resolve(output, 'checks.json'), JSON.stringify({ passed: results, pageErrors: failures, writes }, null, 2));
} finally {
  await browser?.close();
  await development?.close();
  if (production) await new Promise((done) => production.httpServer.close(done));
}
