import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

// Render the real badge, without a browser, API calls or authentication fixtures.
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' });
after(() => server.close());
const { StateBadge, Collection } = await server.ssrLoadModule('/src/pages/Home.tsx');
const { MissionProgress } = await server.ssrLoadModule('/src/components/MissionProgress.tsx');
const { missions, lockedProgress } = await server.ssrLoadModule('/src/features/missions/model.ts');

const badge = (state, missionCode) => renderToStaticMarkup(createElement(StateBadge, { state, missionCode }));

test('moderation wait and active result missions have different presentation without changing state', () => {
  for (const code of ['MAKE_A_WISH', 'SAY_SOMETHING']) {
    const html = badge('PENDING', code);
    assert.match(html, /검수 중이에요/);
    assert.doesNotMatch(html, /글자 획득 완료/);
  }
  for (const code of ['SYNC_ME', 'FAST_TAP']) {
    const html = badge('PENDING', code);
    assert.match(html, /진행 중이에요/);
    assert.doesNotMatch(html, /검수 중이에요|글자 획득 완료/);
  }
});

test('only server completion is labelled as an earned letter', () => {
  for (const state of ['LOCKED', 'UNLOCKED', 'PENDING']) {
    assert.doesNotMatch(badge(state, 'SHOW_ME'), /글자 획득 완료/);
  }
  assert.match(badge('COMPLETED', 'SHOW_ME'), /글자 획득 완료/);
});

const { LetterSticker, letterAssets } = await server.ssrLoadModule('/src/components/LetterSticker.tsx');
test('uncollected artwork never gets reward sparkles or celebration, even if celebration is requested', () => {
  const html = renderToStaticMarkup(createElement(LetterSticker, { code: 'SHOW_ME', collectionState: 'uncollected', celebrate: true }));
  assert.match(html, /is-uncollected/);
  assert.doesNotMatch(html, /letter-sparkle|celebrate/);
  const earned = renderToStaticMarkup(createElement(LetterSticker, { code: 'SHOW_ME', collectionState: 'collected', celebrate: true }));
  assert.match(earned, /is-collected/);
  assert.match(earned, /letter-sparkle/);
});

test('the five distinct mission images are present in the production public directory', async () => {
  const { readFile } = await import('node:fs/promises');
  assert.equal(new Set(Object.values(letterAssets)).size, 5);
  for (const asset of Object.values(letterAssets)) {
    const bytes = await readFile(new URL('../public' + asset, import.meta.url));
    assert.equal(bytes.subarray(0, 4).toString(), 'RIFF');
    assert.equal(bytes.subarray(8, 12).toString(), 'WEBP');
    assert.ok(bytes.length < 150_000, 'individual optimized letter stays below 150KB');
  }
});

const progress = (mission, state, options = {}) => renderToStaticMarkup(createElement(MissionProgress, {
  mission, signedIn: true, data: { preview: false, serverLoaded: true, nickname: null, progress: { ...lockedProgress, [mission.code]: state }, ...options.data }, ...options,
}));
test('five mission progress cards preserve pending versus earned states and expose one native hint disclosure', () => {
  for (const mission of missions) {
    for (const state of ['LOCKED', 'UNLOCKED', 'PENDING', 'COMPLETED']) {
      const html = progress(mission, state);
      assert.ok(html.includes(mission.name));
      assert.ok(html.includes(mission.hint));
      assert.match(html, /<details><summary>미션 힌트 보기<\/summary>/);
      if (state === 'COMPLETED') assert.match(html, /글자 획득 완료/);
      else assert.doesNotMatch(html, /글자 획득 완료|letter-sparkle/);
      if (state === 'PENDING') assert.match(html, ['MAKE_A_WISH', 'SAY_SOMETHING'].includes(mission.code) ? /검수 중이에요/ : /진행 중이에요/);
      assert.doesNotMatch(html, /<form|<input|\/t\//);
    }
  }
});
test('unknown and signed-out progress never presents a false earned record', () => {
  for (const mission of missions) {
    const html = progress(mission, 'COMPLETED', { signedIn: false, data: { preview: false, serverLoaded: false, nickname: null, progress: { ...lockedProgress, [mission.code]: 'COMPLETED' } } });
    assert.match(html, /로그인 후 내 기록/);
    assert.doesNotMatch(html, /글자 획득 완료|letter-sparkle|서버에 저장된 획득 기록/);
  }
});

test('combined mission cards keep unique accessible heading IDs', () => {
  const html = renderToStaticMarkup(createElement('div', null, ...missions.map(mission => createElement(MissionProgress, {
    key: mission.code, mission, signedIn: false, data: { preview: false, progress: lockedProgress, nickname: null },
  }))));
  const ids = [...html.matchAll(/<h2 id="([^"]+)"/g)].map(match => match[1]);
  assert.equal(ids.length, 5);
  assert.equal(new Set(ids).size, 5);
  for (const id of ids) assert.ok(html.includes('aria-labelledby="' + id + '"'));
});

test('public collection has five Korean labels, no invented participation count and neutral copy', () => {
  const previousWindow = globalThis.window;
  globalThis.window = { location: { href: 'http://localhost/', origin: 'http://localhost' } };
  try {
    const html = renderToStaticMarkup(createElement(Collection, { data: { preview: false, progress: lockedProgress, nickname: null } }));
    assert.deepEqual([...html.matchAll(/class="slot-label">([^<]+)</g)].map(match => match[1]), ['소개', '취향', '소원', '탭', '응원']);
    assert.match(html, /획득한 글자는 여기에 모여요\./);
    assert.doesNotMatch(html, /collection-count|로그인 후 확인|미참여/);
    for (const mission of missions) assert.ok(html.includes(mission.name));
  } finally {
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
  }
});
