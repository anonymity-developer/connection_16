import test,{after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createServer} from 'vite';
import {createElement} from 'react';import {renderToStaticMarkup} from 'react-dom/server';
const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom',logLevel:'error'});after(()=>server.close());
const {createExperience}=await server.ssrLoadModule('/src/dev/experience-store.ts');
const {chooseLanding}=await server.ssrLoadModule('/src/dev/FountainExperience.tsx');
const {missions}=await server.ssrLoadModule('/src/features/missions/model.ts');
const {localPhase}=await server.ssrLoadModule('/src/features/results/timing.ts');
const input={content:'가상 소원',templateId:'PLAIN',style:'ROSE',anonymous:true,expectedVersion:null};

test('Docker-local development questionnaire stays identical to the review-only proposal',async()=>{
 const [fixture,proposal]=await Promise.all([
  readFile(new URL('../src/dev/sync-questionnaire.proposal.json',import.meta.url),'utf8'),
  readFile(new URL('../../docs/sync-questionnaire.proposal.json',import.meta.url),'utf8'),
 ]);
 assert.deepEqual(JSON.parse(fixture),JSON.parse(proposal));
});
test('approved display copy changes only names while mission codes, order and routes remain stable',()=>{
 assert.deepEqual(missions.map(m=>m.name),['SHOW ME','SYNC ME','A WISH COME TRUE','FAST TAP','YOU GOT THIS']);assert.deepEqual(missions.map(m=>m.code),['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING']);assert.equal(missions[4].slug,'say-something');assert.equal(missions[2].slug,'make-a-wish');assert.deepEqual(missions.map(m=>m.description),['나를 소개하는 프로필을 완성해요!','나와 잘 맞는 동료를 찾아봐요!','나의 소원을 남기고, 다른 소원을 댓글로 응원해봐요!','10초 동안 최고 탭 기록에 도전해요!','SSAFY 16기 모두를 향한 응원의 메시지를 남겨요!']);for(const m of missions)assert.ok(m.encouragement);
});
test('SHOW ME preview blocks incomplete input and completes without NFC or real consent',()=>{
 const s=createExperience();assert.throws(()=>s.saveProfile('','',[],false));assert.equal(s.progress.SHOW_ME,'LOCKED');s.saveProfile('가상','안녕하세요',['Python'],true);assert.equal(s.progress.SHOW_ME,'COMPLETED');s.saveProfile('가상','수정',['Python'],true);assert.equal(s.progress.SHOW_ME,'COMPLETED');
});
test('SYNC draft choices are validated and first local result remains unchanged',async()=>{
 const s=createExperience();s.unlockTag('SYNC_ME');assert.equal(s.progress.FAST_TAP,'LOCKED');assert.equal(s.progress.MAKE_A_WISH,'LOCKED');assert.equal(s.progress.SAY_SOMETHING,'LOCKED');const q=await s.results.questions();assert.equal(q.questions.length,6);await assert.rejects(s.results.submitSync(q.version,[]));const answers=q.questions.map(q=>({questionId:q.id,optionId:q.options[0].id}));const result=await s.results.submitSync(q.version,answers);assert.equal(s.progress.SYNC_ME,'COMPLETED');assert.match(result.type.title,/가상 결과/);assert.deepEqual(await s.results.submitSync(q.version,[]),result);
});
test('wish preview request is one slot, idempotent, earns A at submission and is private until publication',async()=>{
 const s=createExperience();await assert.rejects(s.content.submit('wishes',false,'locked',input));s.unlockTag('MAKE_A_WISH');const n=s.feeds.wishes.length;const saved=await s.content.submit('wishes',false,'same-key',input);assert.equal(s.feeds.wishes.length,n);assert.equal(s.progress.MAKE_A_WISH,'COMPLETED');assert.deepEqual(await s.content.submit('wishes',false,'same-key',input),saved);s.moderate('wishes','REJECTED');assert.equal(s.feeds.wishes.length,n);assert.equal(s.progress.MAKE_A_WISH,'COMPLETED');await s.content.submit('wishes',true,'edit-key',{...input,expectedVersion:s.slots.wishes.version});s.moderate('wishes','APPROVED');assert.equal(s.progress.MAKE_A_WISH,'COMPLETED');assert.equal(s.feeds.wishes.length,n+1);assert.equal(s.feeds.wishes[0].anonymous,true);assert.equal(s.slots.wishes.id,saved.id);
});
test('failed coin submission produces no new slot; both landing outcomes have identical reward rules',async()=>{
 assert.equal(chooseLanding(.5,.34),'statue');assert.equal(chooseLanding(.1,.8),'water');assert.equal(chooseLanding(.5,.8),'water');const s=createExperience();s.unlockTag('MAKE_A_WISH');s.fail();await assert.rejects(s.content.submit('wishes',false,'retry',input));assert.equal(s.slots.wishes.id,null);assert.equal(s.progress.MAKE_A_WISH,'UNLOCKED');await s.content.submit('wishes',false,'retry',input);assert.equal(s.progress.MAKE_A_WISH,'COMPLETED');assert.equal(s.slots.wishes.landingZone,null);assert.equal(s.slots.wishes.luckScore,null);
});
test('FAST preview preserves five second preparation and ten second play, with no finish before time',async()=>{
 const real=Date.now;let clock=2000000000000;Date.now=()=>clock;
 try{const s=createExperience();s.unlockTag('FAST_TAP');const a=await s.results.start('dev');assert.equal(Date.parse(a.startsAt)-clock,5000);assert.equal(Date.parse(a.endsAt)-Date.parse(a.startsAt),10000);assert.equal(localPhase(4999,5000,15000),'COUNTDOWN');assert.equal(localPhase(5000,5000,15000),'PLAYING');assert.equal(localPhase(15000,5000,15000),'FINISHED');await assert.rejects(s.results.finish({attemptId:a.attemptId,taps:[0],interruptions:[]}));clock+=15000;const payload={attemptId:a.attemptId,taps:Array.from({length:101},(_,i)=>i*90),interruptions:[]};const result=await s.results.finish(payload);assert.equal(result.tapCount,101);assert.equal(s.progress.FAST_TAP,'COMPLETED');assert.deepEqual(await s.results.finish(payload),result);assert.equal((await s.results.tap()).officialAvailable,false);assert.equal((await createExperience().results.tap()).officialAvailable,true);}finally{Date.now=real;}
});
test('FAST preview cancellation distinguishes preparation and play; interrupted slot is not reopened',async()=>{
 const real=Date.now;let clock=2000000000000;Date.now=()=>clock;
 try{const s=createExperience();s.unlockTag('FAST_TAP');let a=await s.results.start('dev');assert.equal((await s.results.cancel(a.attemptId)).status,'CANCELLED');assert.equal((await s.results.tap()).officialAvailable,true);a=await s.results.start('new');clock+=6000;assert.equal((await s.results.cancel(a.attemptId)).status,'INTERRUPTED');assert.equal((await s.results.tap()).officialAvailable,false);}finally{Date.now=real;}
});
test('comment and cheer heart simulations are separate, idempotent and reversible',async()=>{
 const s=createExperience();const c=await s.reactions.submit(100,'comment-key','응원해요',false);assert.equal(c.status,'QUEUED');assert.equal((await s.reactions.comments(100)).items.length,0);assert.equal((await s.reactions.submit(100,'comment-key','응원해요',false)).id,c.id);assert.equal((await s.reactions.own(100)).items.length,1);s.moderate('wishes','APPROVED');assert.equal((await s.reactions.comments(100)).items.length,1);s.moderate('wishes','REJECTED');assert.equal((await s.reactions.comments(100)).items.length,0);assert.equal((await s.reactions.setHeart(200,true)).count,3);assert.equal((await s.reactions.setHeart(200,true)).count,3);assert.equal((await s.reactions.setHeart(200,false)).count,2);assert.equal(s.progress.SAY_SOMETHING,'LOCKED');
});
test('scenario controls create coherent mock records without touching real server ports',async()=>{
 const s=createExperience();s.scenario('FAST_TAP','PENDING');assert.equal((await s.results.tap()).attempt.status,'REVIEW_REQUIRED');s.scenario('MAKE_A_WISH','COMPLETED');assert.equal((await s.content.own('wishes')).approvedRevision.status,'APPROVED');s.scenario('MAKE_A_WISH','UNLOCKED');assert.equal((await s.content.own('wishes')).id,null);s.emptyFeed('wishes');assert.equal((await s.content.feed('wishes')).items.length,0);
});
test('memory simulations complete without fetch and production entry never imports the experience',async()=>{
 const before=globalThis.fetch;let calls=0;globalThis.fetch=()=>{calls++;throw new Error('No network permitted');};
 try{const s=createExperience();await s.content.feed('wishes');await s.results.questions();await s.reactions.setHeart(200,true);s.unlockTag('SAY_SOMETHING');assert.equal((await s.content.submit('cheers',false,'dev',input)).missionCompleted,true);assert.equal(calls,0);}finally{globalThis.fetch=before;}
 const entry=await readFile(new URL('../src/main.tsx',import.meta.url),'utf8');assert.doesNotMatch(entry,/Experience|experience-store|experience\.html/);const dev=await readFile(new URL('../src/dev/ExperienceApp.tsx',import.meta.url),'utf8');assert.match(dev,/if\(import\.meta\.env\.DEV/);const html=await readFile(new URL('../experience.html',import.meta.url),'utf8');assert.match(html,/connect-src 'none'/);
});
test('real React experience home renders all five choices, neutral virtual collection and shared copy',async()=>{
 const {ExperienceApp}=await server.ssrLoadModule('/src/dev/ExperienceApp.tsx');const previous=globalThis.window;globalThis.window={location:{href:'http://localhost/experience.html',origin:'http://localhost'}};
 try{const html=renderToStaticMarkup(createElement(ExperienceApp));assert.match(html,/개발용 미리보기 · 실제 저장 없음/);for(const m of missions)assert.ok(html.includes(m.name));assert.match(html,/우리를 찾아봐!/);assert.match(html,/0<\/b> \/ 5 획득/);assert.doesNotMatch(html,/나를 찾아봐!|SAY 파이팅!/);}finally{if(previous===undefined)delete globalThis.window;else globalThis.window=previous;}
});
