import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom', logLevel: 'error' });
after(() => server.close());
const { createSandbox } = await server.ssrLoadModule('/src/features/admin/sandbox.ts');
const { MissionRuntimeContext } = await server.ssrLoadModule('/src/features/admin/MissionRuntime.tsx');
const { SyncResultCard } = await server.ssrLoadModule('/src/features/results/SyncMission.tsx');
const definition = { version: 'test', source: 'EXAMPLE', definition: { title: '검사용 예시', axes: [{ id: 'talk', negative: 'L', positive: 'W', tie: 'NEGATIVE' }], questions: [{ id: 'q', text: '검사 질문', options: [{ id: 'a', label: '검사 선택', scores: { talk: 1 } }] }], types: [{ code: 'W', title: '검사 결과', description: '가상 결과' }] } };
const catalog = [{id:1,name:'검사 산업',category:'INDUSTRY'},{id:2,name:'검사 분야',category:'DEVELOPMENT'},{id:3,name:'검사 스택',category:'TECH_STACK'}];
const input = { nickname: '검사 별명', bio: '소개', githubUrl: null, blogUrl: null, expectedVersion: 0, interestIds: [1,2,3], acceptPolicies: ['PARTICIPATION', 'PROFILE_PUBLICATION'].map(type => ({ type, version: 'admin-preview' })) };

test('admin preview requires all profile groups and reset drops all example state', async () => {
  const { runtime } = createSandbox(definition, catalog);
  await assert.rejects(runtime.participation.saveProfile({...input,bio:null}),/소개와 관심사/);
  await assert.rejects(runtime.participation.saveProfile({...input,interestIds:[1]}),/소개와 관심사/);
  assert.equal(runtime.showMeCompleted, false);
  await runtime.participation.saveProfile(input);
  assert.equal(runtime.showMeCompleted, true);
  await assert.rejects(runtime.participation.saveProfile(input), /다시 불러와/);
  const fresh = createSandbox(definition, catalog);
  assert.equal(fresh.runtime.showMeCompleted, false);
  assert.equal((await fresh.runtime.participation.profile()).profile, null);
});

test('all preview content, comments, hearts, profile and SYNC actions work with fetch forbidden', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('Unexpected live request'); };
  try {
    const sandbox = createSandbox(definition, catalog), { runtime } = sandbox;
    await runtime.participation.profile(); await runtime.participation.interests(); await runtime.participation.event();
    await runtime.participation.saveProfile(input);
    const q = await runtime.results.questions();
    const result = await runtime.results.submitSync(q.version, [{ questionId: 'q', optionId: 'a' }]);
    assert.equal(result.type.code, 'W'); assert.deepEqual((await runtime.results.sync()).result, result);
    const wish = { content: '미리보기 소원', templateId: 'PLAIN', style: 'ROSE', anonymous: true, expectedVersion: null };
    const saved = await runtime.content.submit('wishes', false, 'same-key', wish);
    assert.deepEqual(await runtime.content.submit('wishes', false, 'same-key', wish), saved);
    await assert.rejects(runtime.content.submit('wishes', false, 'same-key', { ...wish, content: '다른 내용' }));
    assert.equal(saved.pendingRevision, null);
    assert.equal(saved.missionCompleted, true);
    assert.equal((await runtime.content.feed('wishes')).items.find(row => row.id === saved.id).content, wish.content);
    await runtime.reactions.submit(saved.id, 'comment-key', '댓글 검사', true);
    await runtime.reactions.submit(saved.id, 'comment-key', '댓글 검사', true);
    assert.equal((await runtime.reactions.own(saved.id)).items.length, 1);
    assert.equal((await runtime.reactions.comments(saved.id)).items.length, 1);
    assert.equal((await runtime.reactions.comments(saved.id)).items[0].profileId, null);
    const cheer = (await runtime.content.feed('cheers')).items[0];
    await runtime.reactions.setHeart(cheer.id, true); await runtime.reactions.setHeart(cheer.id, true);
    assert.equal((await runtime.reactions.heart(cheer.id)).count, 1);
    assert.equal((await runtime.reactions.hearts([cheer.id]))[cheer.id].liked, true);
    assert.equal((await runtime.reactions.setHeart(cheer.id, false)).count, 0);
  } finally { globalThis.fetch = original; }
});

test('preview TAP cannot finish during countdown, keeps one result and can restart after reset', async () => {
  const { runtime } = createSandbox(definition, catalog), now = Date.now;
  try {
    const start = await runtime.results.start('first-key');
    await assert.rejects(runtime.results.finish({ attemptId: start.attemptId, taps: [1], interruptions: [] }), /시간이 끝난/);
    Date.now = () => Date.parse(start.endsAt) + 1;
    assert.equal((await runtime.results.finish({ attemptId: start.attemptId, taps: [1, 200], interruptions: [] })).tapCount, 2);
    assert.equal((await runtime.results.finish({ attemptId: start.attemptId, taps: [], interruptions: [] })).tapCount, 2);
    assert.equal((await createSandbox(definition, catalog).runtime.results.tap()).officialAvailable, true);
  } finally { Date.now = now; }
});

test('admin SYNC result cannot navigate into real participant discovery or record visits', () => {
  const { runtime } = createSandbox(definition, catalog);
  const html = renderToStaticMarkup(createElement(MissionRuntimeContext.Provider, { value: runtime }, createElement(SyncResultCard, { result: { type: definition.definition.types[0] } })));
  assert.match(html, /관리자 미리보기 결과/);
  assert.doesNotMatch(html, /href="\/discovery/);
});
