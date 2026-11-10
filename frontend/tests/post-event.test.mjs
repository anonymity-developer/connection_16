import test,{after} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'vite';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {emptyReport,reviewReport} from './fixtures/post-event.mjs';
const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom',logLevel:'error'});after(()=>server.close());
const {ResultSummary}=await server.ssrLoadModule('/src/features/operations/ResultsPanel.tsx');
const {resultsApi,resultChanges}=await server.ssrLoadModule('/src/features/operations/results-api.ts');
function render(report,previous){const old=globalThis.window;globalThis.window={location:{href:'http://localhost/',origin:'http://localhost'}};try{return renderToStaticMarkup(createElement(ResultSummary,{report,previous}));}finally{if(old===undefined)delete globalThis.window;else globalThis.window=old;}}
test('zero data stays zero, SHOW ME has no NFC denominator, no fake outcomes or funnel UI',()=>{
 const html=render(emptyReport);assert.match(html,/가입자/);assert.match(html,/0 <small>명/);assert.match(html,/해당 없음/);assert.match(html,/유효 후보가 없어요/);assert.match(html,/체류 시간 집계 불가/);assert.doesNotMatch(html,/전환율 \d|확정 완료|추첨하기|삭제하기|<svg/);
});
test('same snapshot shows pending moderation, exact provisional ties, overlaps and safe escaped values',()=>{
 const report={...reviewReport,data:{...reviewReport.data,policyDecisions:['<img src=x onerror=alert(1)>']}};
 const html=render(report);assert.match(html,/공동 순위 · 검토 필요/);assert.match(html,/복수 부문 1위 후보/);assert.match(html,/점수 0/);assert.match(html,/REVIEW_REQUIRED/);assert.match(html,/YOU GOT THIS/);assert.match(html,/&lt;img/);assert.doesNotMatch(html,/<img|mattermost_id|real_name|content_text/);
});
test('refresh comparison distinguishes data fingerprints and reports real metric changes only',()=>{
 const next={...emptyReport,snapshotId:'2'.repeat(64),data:{...emptyReport.data,metrics:[{...emptyReport.data.metrics[0],value:2},emptyReport.data.metrics[1]]}};
 assert.deepEqual(resultChanges(emptyReport,next),[{key:'registered',before:0,after:2}]);assert.match(render(next,emptyReport),/자료 변경/);assert.match(render(emptyReport,emptyReport),/자료 동일/);
});
test('export uses admin server route, no-store and a matching snapshot without participant log export',async()=>{
 const old=globalThis.fetch;let called;
 globalThis.fetch=async(url,options)=>{called={url,options};return new Response('fixture',{status:200,headers:{'Content-Type':'text/csv'}});};
 try{const blob=await resultsApi.download('csv','a'.repeat(64));assert.equal(await blob.text(),'fixture');assert.equal(called.url,'/api/v1/admin/results/export?format=csv&snapshot='+'a'.repeat(64));assert.equal(called.options.cache,'no-store');assert.equal(called.options.credentials,'same-origin');assert.doesNotMatch(called.url,/participants/);}finally{globalThis.fetch=old;}
});
test('download rejects unauthorized, stale snapshot and server failure responses',async()=>{
 const old=globalThis.fetch;try{for(const status of [401,403,409,503]){globalThis.fetch=async()=>new Response(JSON.stringify({code:'DENIED',message:'fixture refusal'}),{status,headers:{'Content-Type':'application/json'}});await assert.rejects(()=>resultsApi.download('json','a'.repeat(64)),e=>e.status===status);}globalThis.fetch=async()=>{throw new Error('network');};await assert.rejects(()=>resultsApi.download('csv','a'.repeat(64)),e=>e.code==='NETWORK_ERROR');}finally{globalThis.fetch=old;}
});
test('limited participant view declares reconciliation purpose and pagination, never export parameters',async()=>{
 const old=globalThis.fetch;let url;globalThis.fetch=async u=>{url=u;return new Response(JSON.stringify({receipts:[]}));};try{await resultsApi.history(7,51);assert.equal(url,'/api/v1/admin/results/participants/7?purpose=RESULT_RECONCILIATION&after=51');assert.doesNotMatch(url,/export/);}finally{globalThis.fetch=old;}
});
