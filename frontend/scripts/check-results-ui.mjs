/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// API contract fixtures only. Does not publish a questionnaire or create official records.
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4473, strictPort: true } }); let browser;
try {
  browser = await chromium.launch(); const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const pageErrors = [], routeErrors = [], checks = []; await page.addLocatorHandler(page.locator('.mission-earned-actions'), async () => { await page.getByRole('button', {name:'계속 참여하기',exact:true}).click(); });
  page.on('pageerror', error => pageErrors.push(error.message));
  let sync = null, available = true, changed = false, state = { officialAvailable: true, attempt: null }, starts = 0, finishes = 0, cancels = 0, key = null, failStart = false, cancelledReplay = false, cancelRace = false, failFinish = false, submitted = null, locked = false, resultReads = 0;
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const questions = { available: true, version: 'test-v1', title: '테스트 전용 취향 질문', questions: [{ id: 'q1', text: '테스트용 첫 번째 선택', options: [{ id: 'a', label: '먼저 말 걸기' }, { id: 'b', label: '먼저 들어보기' }] }, { id: 'q2', text: '테스트용 두 번째 선택', options: [{ id: 'a', label: '같이 만들기' }, { id: 'b', label: '깊게 살펴보기' }] }] };
  const stored = { questionnaireVersion: 'test-v1', type: { code: 'TEST', title: '테스트 전용 연결형', description: '브라우저 검사 전용 결과이며 실제 운영 성향이 아닙니다.' }, axisScores: { example: 1 }, submittedAt: '2026-01-01T00:00:00Z' };
  function newAttempt(now) { return { attemptId: starts, generation: starts, status: 'COUNTDOWN', serverNow: new Date(now).toISOString(), startsAt: new Date(now + 5000).toISOString(), endsAt: new Date(now + 15000).toISOString(), submitDeadline: new Date(now + 20000).toISOString(), tapCount: null, reviewReason: null, provisional: false }; }
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), path = new URL(req.url()).pathname.replace('/api/v1', ''), method = req.method();
      const reply = (status, value) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) });
      if (method === 'POST' && path === '/missions/show-me/reconcile') { assert.ok(req.headers()['x-xsrf-token']); return reply(200, { newlyCompleted: false }); }
      if (method === 'GET' && path === '/auth/config') return reply(200, { available: true, passwordMinLength: 15, passwordMaxLength: 64 });
      if (method === 'GET' && path === '/discovery/graph') return reply(200, { self: { id: 1, nickname: '테스트' }, nodes: [], edges: [], truncated: false });
      if (method === 'GET' && path === '/users/me') return reply(200, { id: 1, username: 'test-participant', nickname: '테스트', onboardingRequired: false });
      if (method === 'GET' && path === '/tags/pending') return reply(200, { present: false });
      if (method === 'GET' && path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'result-test-csrf' });
      if (method === 'GET' && path === '/event') return reply(200, { status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: false });
      if (method === 'GET' && path === '/missions/collection') return reply(200, { missions: codes.map((code, index) => ({ id: index + 1, code, state: locked ? 'LOCKED' : code === 'SYNC_ME' && sync || code === 'FAST_TAP' && state.attempt?.status === 'VALID' ? 'COMPLETED' : ['SYNC_ME', 'FAST_TAP'].includes(code) ? 'UNLOCKED' : 'LOCKED', active: true })), completedCount: Number(!!sync) + Number(state.attempt?.status === 'VALID'), completion: { complete: false, completionOrder: null, completedAt: null, awardConfirmed: false } });
      if (method === 'GET' && (path.startsWith('/sync/') || path.startsWith('/tap/'))) resultReads++;
      if (method === 'GET' && path === '/sync/me') return reply(200, { result: sync });
      if (method === 'GET' && path === '/sync/questions') return reply(200, available ? questions : { available: false, version: null, title: null, questions: [] });
      if (method === 'GET' && path === '/tap/me') return reply(200, state);
      assert.equal(req.headers()['x-xsrf-token'], 'result-test-csrf');
      if (path === '/sync/submit') { const body = req.postDataJSON(); assert.deepEqual(Object.keys(body).sort(), ['answers', 'questionnaireVersion']); if (changed) return reply(409, { code: 'QUESTIONNAIRE_CHANGED', message: '질문 버전이 변경됐어요. 다시 불러와 주세요.' }); assert.equal(body.answers.length, 2); sync = stored; return reply(200, stored); }
      if (path === '/tap/start') { const next = req.headers()['idempotency-key']; assert.match(next, /^[A-Za-z0-9_-]{16,64}$/); if (key) assert.equal(next, key); key = next; starts++; if (failStart) { failStart = false; return reply(503, { code: 'UNAVAILABLE', message: '시작 응답을 확인하지 못했어요.' }); } if (cancelledReplay) { cancelledReplay = false; const old = { ...newAttempt(await page.evaluate(() => Date.now())), status: 'CANCELLED' }; state = { officialAvailable: true, attempt: null }; return reply(200, old); } state = { officialAvailable: false, attempt: newAttempt(await page.evaluate(() => Date.now())) }; return reply(200, state.attempt); }
      if (path === '/tap/cancel' && cancelRace) { cancelRace = false; const old = { ...state.attempt, status: 'CANCELLED' }; starts++; state = { officialAvailable: false, attempt: { ...newAttempt(Date.now() - 6000), status: 'PLAYING' } }; return reply(200, old); }
      if (path === '/tap/cancel') { cancels++; assert.equal(req.postDataJSON().attemptId, state.attempt.attemptId); const before = await page.evaluate(() => Date.now()) < Date.parse(state.attempt.startsAt); const attempt = { ...state.attempt, status: before ? 'CANCELLED' : 'INTERRUPTED' }; state = { officialAvailable: before, attempt: before ? null : attempt }; if (before) key = null; return reply(200, attempt); }
      if (path === '/tap/finish') { finishes++; const body = req.postDataJSON(); assert.deepEqual(Object.keys(body).sort(), ['attemptId', 'interruptions', 'taps']); assert.equal('tapCount' in body, false); if (submitted) assert.deepEqual(body, submitted); submitted = body; if (failFinish) { failFinish = false; return reply(503, { code: 'UNAVAILABLE', message: '전송 응답을 확인하지 못했어요.' }); } state = { officialAvailable: false, attempt: { ...state.attempt, status: 'VALID', tapCount: body.taps.length, provisional: true } }; return reply(200, state.attempt); }
      throw new Error(`Unexpected ${method} ${path}`);
    } catch (error) { routeErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '계약 검사 오류' }) }); }
  });
  const visit = path => page.goto(`http://127.0.0.1:4473${path}`);
  const record = name => { checks.push(name); console.log(`PASS ${name}`); };
  await page.clock.install();
  await visit('/missions/sync-me'); await page.getByRole('button', { name: '나의 성향 확인하기', exact: true }).waitFor();
  await page.getByRole('button', { name: '나의 성향 확인하기', exact: true }).click(); await page.getByRole('alert').waitFor(); assert.equal(sync, null);
  await page.getByRole('radio', { name: '먼저 말 걸기', exact: true }).check(); await page.getByRole('radio', { name: '같이 만들기', exact: true }).check();
  for (const width of [360, 375, 390, 412, 430, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false);
    for (const heading of await page.locator('h1,h2').all()) assert.ok(await heading.evaluate(el => el.getBoundingClientRect().height / Number.parseFloat(getComputedStyle(el).lineHeight) <= 2.1));
    await page.screenshot({ path: resolve(output, `sync-questions-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 900 }); await page.getByRole('button', { name: '나의 성향 확인하기', exact: true }).click();
  await page.getByRole('heading', { name: '나의 성향 결과', exact: true }).waitFor(); await page.getByText('두 번째 S를 획득했어요!', { exact: true }).waitFor(); assert.equal(await page.getByRole('radio').count(), 0);
  await page.reload(); await page.getByRole('heading', { name: '나의 성향 결과', exact: true }).waitFor(); await page.screenshot({ path: resolve(output, 'sync-result-390.png'), fullPage: true });
  record('SYNC answers are required, only option IDs are sent, server result is fixed and restored at five widths');
  sync = null; changed = true; await visit('/missions/sync-me'); await page.getByRole('radio', { name: '먼저 들어보기', exact: true }).check(); await page.getByRole('radio', { name: '깊게 살펴보기', exact: true }).check(); await page.getByRole('button', { name: '나의 성향 확인하기', exact: true }).click(); await page.getByRole('button', { name: '질문과 저장된 결과 다시 불러오기', exact: true }).waitFor(); assert.equal(sync, null);
  changed = false; available = false; await page.getByRole('button', { name: '질문과 저장된 결과 다시 불러오기', exact: true }).click(); await page.getByText('성향 질문을 준비하고 있어요.', { exact: false }).waitFor(); assert.equal(await page.getByRole('radio').count(), 0);
  record('changed or unconfigured questionnaires never produce a local result');
  await visit('/missions/fast-tap'); await page.getByRole('button', { name: '기록 없이 연습하기', exact: true }).click(); await page.clock.fastForward(5100); await page.getByRole('button', { name: '탭 영역', exact: true }).click(); await page.clock.fastForward(10000); await page.getByText('연습 결과 1회', { exact: false }).waitFor(); assert.equal(starts, 0); assert.equal(finishes, 0);
  record('practice uses no official start or finish request and never awards a letter');
  failStart = true; await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click(); await page.getByRole('alert').waitFor(); const firstKey = key;
  await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click(); await page.getByRole('button', { name: '준비 중 취소하기', exact: true }).waitFor(); assert.equal(key, firstKey);
  await page.getByRole('button', { name: '준비 중 취소하기', exact: true }).click(); await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).waitFor(); assert.equal(cancels, 1);
  record('uncertain start retries reuse an idempotency key and countdown cancellation restores availability');
  // Another tab cancelled the first request before this tab received its uncertain retry.
  failStart = true;
  await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click();
  await page.getByRole('alert').waitFor(); const cancelledKey = key; cancelledReplay = true;
  await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click();
  await page.getByText('취소된 요청을 확인했어요. 현재 공식 도전 상태를 다시 불러왔어요.', { exact: true }).waitFor();
  await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).waitFor();
  assert.equal(key, cancelledKey); key = null;
  record('cancelled start replay reconciles the current slot rather than consuming a fake chance');
  await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click(); await page.getByRole('button', { name: '준비 중 취소하기', exact: true }).waitFor(); await page.clock.fastForward(5100);
  const area = page.getByRole('button', { name: '탭 영역', exact: true }); await area.waitFor(); await page.waitForFunction(() => document.querySelector('.tap-area')?.textContent?.startsWith('탭!'));
  assert.equal(await area.evaluate(el => getComputedStyle(el).touchAction), 'none');
  await area.click(); await page.clock.fastForward(100); await area.click(); await page.clock.fastForward(100); await area.focus(); await page.keyboard.press('Space');
  for (const width of [360, 375, 390, 412, 430, 768, 1440]) { await page.setViewportSize({ width, height: 900 }); assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth), false); await page.screenshot({ path: resolve(output, `tap-playing-${width}.png`), fullPage: true }); }
  failFinish = true; await page.clock.fastForward(10000); await page.getByRole('button', { name: '같은 기록 다시 전송하기', exact: true }).waitFor(); await page.getByRole('button', { name: '같은 기록 다시 전송하기', exact: true }).click(); await page.getByRole('heading', { name: '도전을 마쳤어요', exact: true }).waitFor(); assert.equal(finishes, 2); assert.equal(submitted.taps.length, 3);
  await page.getByText('F를 획득했어요!', { exact: true }).waitFor(); await page.setViewportSize({ width: 390, height: 900 }); await page.screenshot({ path: resolve(output, 'tap-valid-390.png'), fullPage: true }); await page.reload(); await page.getByRole('heading', { name: '도전을 마쳤어요', exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).count(), 0);
  record('official pointer and keyboard events are submitted once, uncertain finish retries the same payload and saved score restores');
  state = { officialAvailable: true, attempt: null }; key = null; submitted = null; await visit('/missions/fast-tap'); await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click(); await page.getByRole('button', { name: '준비 중 취소하기', exact: true }).waitFor(); await page.clock.fastForward(5100);
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide'))); await page.getByRole('heading', { name: '공식 도전이 중단됐어요', exact: true }).waitFor(); assert.equal(state.officialAvailable, false);
  await page.reload(); await page.getByRole('heading', { name: '공식 도전이 중단됐어요', exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).count(), 0);
  record('page exit after play starts interrupts the official slot and refresh never creates a new chance');
  state = { officialAvailable: true, attempt: null }; key = null; submitted = null; await visit('/missions/fast-tap');
  await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).click(); await page.getByRole('button', { name: '준비 중 취소하기', exact: true }).waitFor();
  cancelRace = true; await page.getByRole('button', { name: '준비 중 취소하기', exact: true }).click();
  await page.getByRole('heading', { name: '진행 중인 공식 도전이 있어요', exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: '공식 도전 시작하기 (1회)', exact: true }).count(), 0);
  record('late cancelled response reloads a newer slot from another tab and never offers a fake official chance');
  state={officialAvailable:false,attempt:{...newAttempt(Date.now()-20000),status:'REVIEW_REQUIRED',tapCount:118,reviewReason:'UNUSUAL_RATE'}};
  await visit('/missions/fast-tap'); await page.getByRole('heading',{name:'이전 규칙의 기록을 정리하고 있어요',exact:true}).waitFor();
  assert.equal(await page.locator('.tap-score').innerText(),'118회');
  await page.getByText('저장된 입력을 서버가 현재 규칙으로 자동 확인하고 있어요. 잠시 뒤 기록을 새로고침해 주세요.',{exact:true}).waitFor();
  assert.equal(await page.getByText('F를 획득했어요!',{exact:true}).count(),0);
  await page.reload(); await page.getByRole('heading',{name:'이전 규칙의 기록을 정리하고 있어요',exact:true}).waitFor();
  assert.equal(await page.locator('.tap-score').innerText(),'118회');
  await page.setViewportSize({width:390,height:900});
  await page.screenshot({path:resolve(output,'tap-review-stored-390.png'),fullPage:true});
  record('review-required scores remain visible after reload and are explicitly stored without claiming F');
  locked=true;
  for(const width of [360,390,430,768,1440]) {
    await page.setViewportSize({width,height:900});
    for(const slug of ['sync-me','fast-tap']) {
      const before=resultReads;
      await visit('/missions/'+slug); await page.waitForLoadState('networkidle');
      await page.getByText('현장의 NFC 태그를 찾아 휴대폰을 가까이 대주세요.',{exact:true}).waitFor();
      assert.equal(await page.locator('.tap-panel, .sync-question-list, .sync-result, .tap-area').count(),0);
      assert.equal(resultReads,before,'locked result missions do not load game APIs');
    }
  }
  record('locked tap and sync pages never mount games or load result APIs at five widths');
  assert.deepEqual(pageErrors, []); assert.deepEqual(routeErrors, []); await writeFile(resolve(output, 'results-contract-checks.json'), JSON.stringify({ kind: 'explicit browser API fixtures; no live questionnaire or official participant records', checks, pageErrors, routeErrors }, null, 2));
} finally { await browser?.close(); await server.httpServer.close(); }
