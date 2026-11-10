import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom', logLevel: 'error' });
after(() => server.close());
const { loadParticipation } = await server.ssrLoadModule('/src/features/participation/refresh.ts');
const { SyncResultCard } = await server.ssrLoadModule('/src/features/results/SyncMission.tsx');
const { createWishFeedback, WishToss } = await server.ssrLoadModule('/src/features/content/WishToss.tsx');
const { missions } = await server.ssrLoadModule('/src/features/missions/model.ts');
const collection = (state = 'LOCKED') => ({ missions: [{ code: 'SHOW_ME', state, active: true }], completedCount: state === 'COMPLETED' ? 1 : 0 });
function api(status = 'OPEN', state = 'LOCKED', granted = true) {
  const calls = []; let reads = 0;
  return { calls,
    collection: async () => { calls.push('GET collection'); return collection(reads++ && granted ? 'COMPLETED' : state); },
    event: async () => ({ status }),
    reconcileShowMe: async () => { calls.push('POST reconcile'); return { newlyCompleted: granted }; },
  };
}
test('OPEN collection reconciles a pre-registered SHOW ME through POST then reloads only server-confirmed completion', async () => {
  const fixture = api(); const result = await loadParticipation(fixture, () => true);
  assert.deepEqual(fixture.calls, ['GET collection','POST reconcile','GET collection']);
  assert.equal(result.collection.completedCount, 1);
});
test('before event, closed, unconfigured, completed or stale loads never invoke the automatic grant', async () => {
  for (const status of ['BEFORE_OPEN','CLOSED','NOT_CONFIGURED']) {
    const fixture = api(status); await loadParticipation(fixture, () => true);
    assert.deepEqual(fixture.calls, ['GET collection']);
  }
  const done = api('OPEN','COMPLETED'); await loadParticipation(done, () => true); assert.deepEqual(done.calls, ['GET collection']);
  const stale = api(); await loadParticipation(stale, () => false); assert.deepEqual(stale.calls, ['GET collection']);
});
test('failed or ineligible reconciliation cannot manufacture an earned letter', async () => {
  const ineligible = api('OPEN','LOCKED',false); const result = await loadParticipation(ineligible, () => true);
  assert.equal(result.collection.completedCount, 0); assert.equal(ineligible.calls.length, 3);
  const failed = api(); failed.reconcileShowMe = async () => { throw new Error('network'); };
  await assert.rejects(loadParticipation(failed, () => true), /network/);
});
test('SYNC result routes to existing recommendations, not the relationship graph', () => {
  const previous = globalThis.window; globalThis.window = { location: { href: 'http://localhost/', origin: 'http://localhost' } };
  try {
    const html = renderToStaticMarkup(createElement(SyncResultCard, { result: { type: { code:'FIXTURE', title:'검토용 성향', description:'테스트 결과' } } }));
    assert.match(html, /href="\/discovery"[^>]*>나와 통하는 동료 만나기/);
    assert.doesNotMatch(html, /graph|궁합|%/);
  } finally { if(previous === undefined) delete globalThis.window; else globalThis.window = previous; }
});
test('wish feedback deduplicates fulfilled revisions and does not depend on luck or reward completion', () => {
  const feedback = createWishFeedback();
  const pending = { id: 1, latestRevision: {id:11,status:'QUEUED'}, missionCompleted:false, luckScore:null, landingZone:null };
  assert.equal(feedback(pending), 11); assert.equal(feedback(pending), null);
  assert.equal(feedback({...pending,latestRevision:{id:12,status:'QUEUED'}}),12);
  assert.equal(feedback({id:null,latestRevision:null}),null);
  assert.equal(pending.missionCompleted,false);assert.equal(pending.luckScore,null);
});
test('wish toss preserves textual moderation status and exposes no score or acquired reward', () => {
  const html = renderToStaticMarkup(createElement(WishToss));
  assert.match(html,/role="status"/);assert.match(html,/A는 첫 검수 승인 후/);
  assert.doesNotMatch(html,/글자 획득 완료|운 점수|landingZone|<button|<input/);
});
test('SAY display name changes consistently while internal identifier, Y and route remain stable', () => {
  const cheer = missions.find(m => m.code === 'SAY_SOMETHING');
  assert.equal(cheer.name,'YOU GOT THIS');assert.equal(cheer.description,'SSAFY 16기 모두를 향한 응원의 메시지를 남겨요!');
  assert.equal(cheer.slug,'say-something');assert.equal(cheer.letter,'Y');
  assert.doesNotMatch(missions.find(m => m.code === 'SHOW_ME').hint,/태그|NFC/);
});

test('a losing concurrent reconciliation reloads the winner record even when newlyCompleted is false', async () => {
  const fixture = api(); fixture.reconcileShowMe = async () => ({ newlyCompleted: false });
  const result = await loadParticipation(fixture, () => true);
  assert.equal(result.collection.completedCount, 1);
  assert.deepEqual(fixture.calls, ['GET collection','GET collection']);
});
