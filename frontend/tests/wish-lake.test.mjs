import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom', logLevel: 'error' });
after(() => server.close());
const { lakePage, WishGallery, WishCard } = await server.ssrLoadModule('/src/features/content/WishGallery.tsx');
const { createWishFeedback } = await server.ssrLoadModule('/src/features/content/WishToss.tsx');
const item = id => ({ id, content: `가상 소원 ${id}`, anonymous: true, nickname: null, profileId: null, templateId:'PLAIN', style:'ROSE', luckScore:null, landingZone:null });
const render = props => renderToStaticMarkup(createElement(WishGallery, props));
test('500 wishes can be reached in 50 fountain groups without duplicate records', () => {
  const items = Array.from({length:500}, (_,i) => item(i+1)); const seen = [];
  for(let page=0;page<50;page++) { const result=lakePage([...items,items[0]],page); assert.equal(result.items.length,10); assert.equal(result.pages,50); seen.push(...result.items.map(x=>x.id)); }
  assert.deepEqual(seen,items.map(x=>x.id)); assert.equal(lakePage(items,999).index,49); assert.equal(lakePage(items,-2).index,0); assert.equal(lakePage([],0).pages,1);
});
test('empty and unknown feeds never manufacture public coins, rewards or participant totals', () => {
  const empty=render({items:[]}); assert.match(empty,/아직 공개된 소원이 없어요/); assert.doesNotMatch(empty,/class="wish-lake-coin"|글자 획득 완료|운 점수/);
  assert.doesNotMatch(render({items:[],feedReady:false}),/아직 공개된 소원이 없어요/);
});
test('public coin buttons stay keyboard accessible and cloud view exposes all public wishes', () => {
  const items=Array.from({length:20},(_,i)=>item(i+1)); const lake=render({items});
  assert.equal((lake.match(/class="wish-lake-coin"/g)||[]).length,10);
  assert.match(lake,/aria-label="소원 1 열기"/); assert.match(lake,/aria-expanded="false"/); assert.match(lake,/aria-controls=/); assert.match(lake,/구름으로 보기/); assert.doesNotMatch(lake,/목록으로 보기/);
  const cloud=render({items,initialView:'cloud'});
  assert.match(cloud,/aria-label="구름 소원 목록"/);
  assert.equal((cloud.match(/<li><button type="button" aria-expanded=/g)||[]).length,20);
  assert.match(cloud,/가상 소원 20/);
});
test('wish cards stay anonymous, escape content and expose only the public body', () => {
  const html=renderToStaticMarkup(createElement(WishCard,{item:{...item(1),anonymous:true,nickname:'MUST_NOT_RENDER',profileId:42,content:'<script>danger()</script>',luckScore:999,landingZone:9}}));
  assert.match(html,/익명의 소원/); assert.match(html,/&lt;script&gt;/); assert.doesNotMatch(html,/MUST_NOT_RENDER|\/profiles\/|999|<script>/);
});
test('explicitly public wishes show the public nickname and profile link without interpreting HTML', () => {
  const previous=globalThis.window;globalThis.window={location:{href:'http://localhost/',origin:'http://localhost'}};
  try {
    const html=renderToStaticMarkup(createElement(WishCard,{item:{...item(1),anonymous:false,nickname:'공개 별명',profileId:42,content:'<script>danger()</script>'}}));
    assert.match(html,/공개 별명/);assert.match(html,/\/discovery\/profiles\/42/);assert.match(html,/&lt;script&gt;/);assert.doesNotMatch(html,/<script>/);
  }finally{globalThis.window=previous;}
});
test('pending receipts animate only after fulfilled revisions, with no public wish or fake A', () => {
  const feedback=createWishFeedback(); const pending={id:1,latestRevision:{id:10,status:'QUEUED'},missionCompleted:false};
  const receipt=feedback(pending); const html=render({items:[],receipt});
  assert.match(html,/wish-toss--in-lake/); assert.match(html,/동전이 분수 위에 안착했어요/); assert.doesNotMatch(html,/A를 획득했어요/); assert.equal(feedback(pending),null);
  assert.doesNotMatch(html,/class="wish-lake-coin"|글자 획득 완료/); assert.doesNotMatch(render({items:[]}),/wish-toss--in-lake/);
});
test('existing moderation feed, submission idempotency and animation reduction remain wired', async () => {
  const source=await readFile(new URL('../src/features/content/ContentMission.tsx',import.meta.url),'utf8');
  assert.match(source,/await contentApi\.submit[\s\S]*setWishReceipt\(wishFeedback\.current\(next\)\)/);
  assert.match(source,/pending\.current = \{ key: crypto\.randomUUID/); assert.match(source,/loadFeed\(feed\.nextCursor!/);
  assert.match(source,/items=\{feed\?\.items \|\| \[\]\}/); assert.doesNotMatch(source,/items=\{.*latestRevision/);
  const css=await readFile(new URL('../src/features/content/wish-lake.css',import.meta.url),'utf8');
  assert.match(css,/prefers-reduced-motion: reduce/); assert.match(css,/animation: none/); assert.match(css,/min-height: 44px/);
});
