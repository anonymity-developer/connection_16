/* global document, window */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { chmod, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';
import { emptyReport } from '../tests/fixtures/post-event.mjs';

// Isolated browser contracts: synthetic rows only, no live administrator or participant records.
let snapshot = '';
const server = await preview({ preview: { host: '127.0.0.1', port: 4573, strictPort: true }, plugins: [{ name: 'admin-measured-dom', configurePreviewServer(server) { server.middlewares.use('/admin-snapshot.html', (req, res) => { if (req.method !== 'GET') { res.statusCode = 405; res.end(); return; } res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(snapshot); }); } }] });
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
let browser;
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  let signedIn = false, allowed = true, previewReads = 0;
  const mutations = [], errors = [], checks = [];
  page.on('pageerror', error => errors.push(error.message));
  const codes = ['SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
  const now = '2026-11-10T01:00:00Z';
  const members = ['검증 은하', '검증 봄빛', '검증 새벽'].map((nickname, index) => ({ id: index + 10, signupOrder: index + 1, username: `fixture-${index + 1}`, nickname, realName: nickname, region: '서울', classNo: index + 1, status: 'ACTIVE', joinedAt: `2026-11-0${index + 5}T01:00:00Z`, lastLoginAt: now, unlockedCount: 5, completedCount: index === 0 ? 5 : 2, completionOrder: index === 0 ? 1 : null, completedAt: index === 0 ? now : null, connectionSeconds: index === 2 ? null : 3600 + index * 60, lastSeenAt: now, activeSessions: index === 0 ? 1 : 0 }));
  const definition = { version: 'test-preview', source: 'EXAMPLE', definition: { title: '관리자 화면 검증용 예시', axes: [{ id: 'talk', negative: 'L', positive: 'W', tie: 'NEGATIVE' }], questions: [{ id: 'first', text: '처음 만난 동료와 대화한다면?', options: [{ id: 'listen', label: '먼저 들어보기', scores: { talk: -1 } }, { id: 'speak', label: '먼저 말 걸기', scores: { talk: 1 } }] }], types: [{ code: 'L', title: '차분한 경청', description: '미리보기용 결과입니다.' }, { code: 'W', title: '반가운 대화', description: '미리보기용 결과입니다.' }] } };
  function slice(items, query) { const offset = Number(query.get('offset') || 0); return { items: items.slice(offset, offset + 25), total: items.length, offset, nextOffset: offset + 25 < items.length ? offset + 25 : null }; }
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.slice('/api/v1'.length);
    const reply = (status, body) => route.fulfill({ status, ...(body === undefined ? {} : { json: body }) });
    if (request.method() !== 'GET') {
      mutations.push(path);
      if (path === '/auth/login') { assert.equal(request.postDataJSON().username, 'admin:contract'); signedIn = true; return reply(200, { id: 1, username: 'admin:contract', nickname: null, onboardingRequired: false, admin: true }); }
      if (path === '/auth/logout') { signedIn = false; return reply(204); }
      return reply(500, { message: 'Preview attempted a real write.' });
    }
    if (path === '/auth/config') return reply(200, { available: true, participantAvailable: false, adminAvailable: true, passwordMinLength: 15, passwordMaxLength: 64 });
    if (path === '/csrf') return reply(200, { headerName: 'X-XSRF-TOKEN', token: 'admin-test-csrf' });
    if (path === '/users/me') return signedIn ? reply(200, { id: 1, username: 'admin:contract', nickname: null, onboardingRequired: false, admin: true }) : reply(401, { code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' });
    if (path === '/tags/pending') return reply(200, { present: false });
    if (!signedIn || !allowed) return reply(403, { code: 'ADMIN_REQUIRED', message: '관리자 권한이 필요해요.' });
    if (path === '/admin/access') return reply(200, { admin: true });
    if (path === '/admin/moderation/tasks' || path === '/admin/content' || path === '/admin/audit-logs') return reply(200, { items: [], nextCursor: null });
    if (path === '/admin/results') return reply(200, emptyReport);
    if (path === '/admin/awards') return reply(200, { ruleVersion: 'fixture-v1', provisional: true, observedAt: now, opensAt: null, submitClosesAt: null, moderationClosesAt: null, eventPhase: 'NOT_CONFIGURED', category: url.searchParams.get('category'), eligibleCompleteCount: 0, total: 0, offset: 0, limit: 20, items: [], invalidProofs: 0, incompleteProofSets: 0, pendingModeration: 0, reviewTotal: 0, reviewRecords: [], pendingDecisions: [] });
    if (path === '/admin/overview') return reply(200, { members: members.length, activeMembers: 3, completeMembers: 1, activeSessions: 1, connectedMembers: 2, connectionSeconds: 7260, trackedSince: now, missions: codes.map((code, i) => ({ code, title: code.replaceAll('_', ' '), unlocked: 3, participated: i < 2 ? 3 : 1, completed: i < 2 ? 3 : 1 })), generatedAt: now });
    if (path === '/admin/members') return reply(200, slice(members.filter(m => !url.searchParams.get('q') || m.nickname.includes(url.searchParams.get('q'))), url.searchParams));
    if (path === '/admin/completions') return reply(200, slice(members.filter(m => m.completionOrder !== null), url.searchParams));
    if (path.startsWith('/admin/members/')) return reply(200, { member: members.find(m => m.id === Number(path.split('/').at(-1))), bio: '화면 검증용 합성 프로필입니다.', interests: ['백엔드', 'Java'], githubUrl: null, blogUrl: null, missions: codes.map(code => ({ code, title: code.replaceAll('_', ' '), state: 'COMPLETED', unlockedAt: now, completedAt: now, participated: true, resultStatus: code === 'FAST_TAP' ? 'VALID' : null, tapCount: code === 'FAST_TAP' ? 25 : null, syncType: null, moderationStatus: null })), connections: [{ startedAt: now, lastSeenAt: now, expiresAt: now, endedAt: now, status: 'LOGOUT', seconds: 3600 }] });
    if (path.startsWith('/admin/rankings/')) return reply(200, { mission: path.split('/').at(-1), basis: '브라우저 검증용 합성 순위', ...slice(members.map((m, i) => ({ rank: i + 1, userId: m.id, nickname: m.nickname, realName: m.realName, region: m.region, classNo: m.classNo, metric: 30 - i * 5, completedAt: now })), url.searchParams) });
    if (path === '/admin/preview/sync') { previewReads++; return reply(200, definition); }
    if (path === '/interests') return reply(200, [{ id: 1, name: '백엔드', category: 'DOMAIN' }, { id: 2, name: 'Java', category: 'TECH_STACK' }]);
    return reply(500, { message: `Unexpected GET ${path}` });
  });
  const visit = path => page.goto('http://127.0.0.1:4573' + path);
  const record = name => { checks.push(name); console.log('PASS ' + name); };
  await visit('/auth'); await page.getByLabel('아이디', { exact: true }).fill('admin:contract'); await page.getByLabel('비밀번호', { exact: true }).fill('a contract test password'); await page.getByRole('button', { name: '로그인하기', exact: true }).click(); await page.waitForURL('**/admin'); await page.getByRole('heading', { name: '다섯 미션 참여', exact: true }).waitFor(); record('shared login redirects administrators without participant onboarding');
  await page.waitForLoadState('networkidle');
  snapshot = (await page.content()).replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '');
  for (const width of [1440, 768, 430, 390, 360]) {
    await page.setViewportSize({ width, height: 1000 });
    for (const [route, name] of [['/admin', 'overview'], ['/admin/members', 'members'], ['/admin/members/10', 'detail'], ['/admin/rankings', 'rankings'], ['/admin/completions', 'completions'], ['/admin/moderation', 'moderation'], ['/admin/moderation?tab=content', 'content'], ['/admin/moderation?tab=audit', 'audit'], ['/admin/moderation?tab=awards', 'awards'], ['/admin/moderation?tab=results', 'results']]) {
      await visit(route); await page.locator('.admin-sheet').waitFor(); await page.waitForLoadState('networkidle');
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `${route} fits ${width}`);
      assert.equal(await page.locator('h1').count(), 1); assert.equal(await page.locator('.admin-sidebar').count(), 1);
      assert.equal(await page.locator('.admin-sidebar nav [aria-current="page"]').count(), 1);
      assert.equal(await page.locator('[role="alert"]').count(), 0);
      await page.screenshot({ path: resolve(output, `admin-${name}-${width}.png`), fullPage: true });
    }
  }
  record('analytics, members, moderation, content, audit, awards and results share one sidebar and heading at five widths');
  await page.setViewportSize({ width: 1440, height: 1000 }); await visit('/admin');
  await page.locator('.admin-sidebar').evaluate(element => { element.dataset.continuity = 'same-shell'; });
  for (const name of ['콘텐츠 관리', '시상 후보', '행사 결과', '미션 미리보기', '참가자', '참여 현황']) {
    const navigation = page.getByRole('navigation', { name: '관리자 메뉴', exact: true });
    await navigation.getByRole('link', { name, exact: true }).click(); await page.getByRole('heading', { name, exact: true, level: 1 }).waitFor(); await page.waitForLoadState('networkidle');
    assert.equal(await page.locator('.admin-sidebar').getAttribute('data-continuity'), 'same-shell');
    assert.equal(await navigation.locator('[aria-current="page"]').innerText(), name);
    assert.equal(await page.locator('[role="alert"]').count(), 0);
  }
  record('sidebar navigation keeps the same mounted shell across previews, analytics, moderation, awards and results');
  await page.setViewportSize({ width: 390, height: 900 }); await visit('/admin'); await page.getByRole('button', { name: '메뉴', exact: true }).click(); await page.getByRole('navigation', { name: '관리자 메뉴', exact: true }).getByRole('link', { name: '참가자', exact: true }).click(); await page.waitForURL('**/admin/members'); assert.equal(await page.getByRole('navigation', { name: '관리자 메뉴', exact: true }).isVisible(), false); record('phone menu opens accessibly and folds after navigation');
  await visit('/admin/members'); await page.getByLabel('이름·닉네임·아이디 검색').fill('봄빛'); await page.getByRole('button', { name: '검색', exact: true }).click(); await page.getByRole('heading', { name: '가입 순서 · 1명', exact: true }).waitFor(); assert.equal(await page.locator('.admin-table tbody tr').count(), 1); await page.goBack(); await page.getByRole('heading', { name: '가입 순서 · 3명', exact: true }).waitFor(); assert.equal(await page.getByLabel('이름·닉네임·아이디 검색').inputValue(), ''); record('participant search keeps original signup order and restores input/count on browser back');
  await visit('/admin/preview'); await page.getByRole('button', { name: '기록 없이 연습하기', exact: true }).waitFor();
  await page.clock.install();
  await page.getByRole('button', { name: '기록 없이 연습하기', exact: true }).click(); await page.clock.fastForward(5100); await page.getByRole('button', { name: '탭 영역', exact: true }).click(); await page.clock.fastForward(10000); await page.getByText('연습 결과 1회', { exact: false }).waitFor();
  await page.getByRole('button', { name: '미리보기 도전 시작하기', exact: true }).click(); await page.clock.fastForward(5100); await page.getByRole('button', { name: '탭 영역', exact: true }).click(); await page.clock.fastForward(10000); await page.getByRole('heading', { name: '미리보기 결과를 확인했어요', exact: true }).waitFor();
  await page.getByLabel('최고 기록 갱신 소감', { exact: true }).fill('미리보기에서만 저장되는 소감');
  await page.getByRole('button', { name: '소감 남기기', exact: true }).click();
  await page.locator('.tap-ranking-list').getByText('미리보기에서만 저장되는 소감', { exact: true }).waitFor();
  assert.equal(await page.getByLabel('최고 기록 갱신 소감', { exact: true }).count(), 0);
  await page.getByRole('button', { name: '미리보기 초기화', exact: true }).click(); await page.getByRole('button', { name: '미리보기 도전 시작하기', exact: true }).waitFor();
  assert.equal(await page.locator('.tap-ranking-list li').count(), 0);
  record('real tap component supports practice, simulated official round and replay reset with no server mutations');
  await page.getByLabel('미리 볼 미션', { exact: true }).selectOption('SYNC_ME'); await page.getByRole('radio', { name: '먼저 말 걸기', exact: true }).check(); await page.getByRole('button', { name: '나의 성향 확인하기', exact: true }).click(); await page.getByText('반가운 대화', { exact: true }).waitFor(); record('real sync component computes an explicit preview-only result from example questions');
  for (const code of ['MAKE_A_WISH', 'SAY_SOMETHING']) {
    await page.getByLabel('미리 볼 미션', { exact: true }).selectOption(code);
    await page.locator('#content-text').fill('미리보기 동작 확인용 문구');
    await page.getByRole('button', { name: code === 'MAKE_A_WISH' ? '동전 던지기' : '응원 보내기', exact: true }).click();
    await page.getByText('공개된 글', { exact: true }).waitFor();
  }
  record('real wish and cheer components publish submitted wishes and cheers immediately in memory with no live content writes');
  const cheer = page.locator('article').filter({ hasText: '관리자 미리보기 예시 응원입니다.' });
  await cheer.getByRole('button', { name: '하트 남기기', exact: true }).click(); await cheer.getByText('하트 1개', { exact: true }).waitFor();
  await cheer.getByRole('button', { name: '하트 취소하기', exact: true }).click(); await cheer.getByText('하트 0개', { exact: true }).waitFor();
  await page.getByLabel('미리 볼 미션', { exact: true }).selectOption('MAKE_A_WISH');
  await page.getByRole('button', { name: '소원 1 열기', exact: true }).click(); await page.getByRole('heading', { name: '발견한 소원', exact: true }).waitFor();
  const wish = page.locator('.wish-lake-detail article').filter({ hasText: '관리자 미리보기 예시 소원입니다.' });
  async function openCloudWish() {
    await page.getByRole('button', { name: '구름으로 보기', exact: true }).click();
    await page.getByRole('list', { name: '구름 소원 목록' }).getByRole('button', { name: /관리자 미리보기 예시 소원입니다/ }).click();
  }
  await openCloudWish();
  await wish.getByRole('button', { name: '댓글 보기', exact: true }).click(); await wish.getByLabel('동료의 소원에 댓글 남기기', { exact: true }).fill('메모리 댓글 승인 확인');
  await wish.getByRole('button', { name: '댓글 남기기', exact: true }).click(); await wish.getByText('공개됨', { exact: true }).waitFor(); await wish.locator('.comment-list').first().getByText('메모리 댓글 승인 확인', { exact: true }).waitFor();
  record('shared fountain, comments and heart controls run entirely in preview memory');
  await page.getByLabel('미리 볼 미션', { exact: true }).selectOption('SHOW_ME'); await page.getByLabel('닉네임', { exact: true }).fill('미리보기 첫인사'); await page.getByRole('checkbox', { name: '참여 안내와 상세 정보 공개 범위를 확인했어요.', exact: true }).check(); await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click(); await page.getByText('미리보기 프로필을 저장했어요.', { exact: false }).waitFor();
  await page.getByRole('heading', { name: '첫 번째 S 준비하기', exact: true }).waitFor();
  await page.getByLabel('한 줄 소개 (선택)', { exact: true }).fill('관리자 화면 확인용 예시 소개'); await page.getByRole('checkbox', { name: '백엔드', exact: true }).check(); await page.getByRole('button', { name: '프로필 저장하기', exact: true }).click(); await page.getByRole('heading', { name: '첫 번째 S를 모았어요', exact: true }).waitFor();
  assert.deepEqual(mutations, ['/auth/login']); record('all five previews preserve participant profile, session identity and official records; only login used POST');
  await page.screenshot({ path: resolve(output, 'admin-preview-390.png'), fullPage: true });
  allowed = false; const before = previewReads; await page.reload(); await page.getByRole('alert').filter({ hasText: '관리자 권한이 필요해요.' }).waitFor(); assert.equal(previewReads, before); assert.equal(await page.locator('.admin-preview-stage').count(), 0); record('revoked administrator access never loads preview data or mounts a preview');
  assert.deepEqual(errors, []); await writeFile(resolve(output, 'admin-contract-checks.json'), JSON.stringify({ kind: 'synthetic browser fixtures, no real accounts or participant writes', checks, mutations, errors }, null, 2));
  // Inspect the rendered synthetic administrator DOM without carrying any credential or auth bypass.
  const inspector = resolve('../.taste-skill/admin-render'); await mkdir(inspector, { recursive: true });
  const launcher = process.platform === 'win32' ? chromium.executablePath() : resolve(inspector, 'chromium.sh');
  if (process.platform !== 'win32') { await writeFile(launcher, `#!/usr/bin/env bash\nexec "${chromium.executablePath()}" --no-sandbox "$@"\n`); await chmod(launcher, 0o700); }
  await new Promise((done, reject) => { const child = spawn(process.execPath, ['scripts/taste.mjs', 'inspect', 'http://127.0.0.1:4573/admin-snapshot.html', '--app', '--widths', '1440,768,390', '--out', '.taste-skill/admin-render'], { cwd: resolve('..'), env: { ...process.env, CHROME_PATH: launcher }, stdio: 'inherit' }); child.on('error', reject); child.on('exit', code => code === 0 ? done() : reject(new Error(`Admin render check exited ${code}`))); });
} finally { await browser?.close(); await new Promise(resolve => server.httpServer.close(resolve)); }
