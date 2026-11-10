import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom',logLevel:'error'});
after(()=>server.close());
const { AwardReportView }=await server.ssrLoadModule('/src/features/operations/AwardsPanel.tsx');
const { awardLabels }=await server.ssrLoadModule('/src/features/operations/awards-api.ts');
const base={ruleVersion:'fixture-v1',provisional:true,observedAt:'2026-01-01T01:00:00Z',opensAt:null,submitClosesAt:null,moderationClosesAt:null,eventPhase:'NOT_CONFIGURED',category:'FIRST_COMPLETION',eligibleCompleteCount:0,total:0,offset:0,limit:20,items:[],invalidProofs:0,incompleteProofSets:0,pendingModeration:0,reviewTotal:0,reviewRecords:[],pendingDecisions:['추첨·최종 배정 미확정']};
function render(report){const previous=globalThis.window;globalThis.window={location:{href:'http://localhost/',origin:'http://localhost'}};try{return renderToStaticMarkup(createElement(AwardReportView,{report}));}finally{if(previous===undefined)delete globalThis.window;else globalThis.window=previous;}}
const row={rank:1,userId:42,nickname:'<script>fixture</script>',profileRegisteredAt:'2026-01-01T00:00:00.000001Z',validCompletionAt:'2026-01-01T00:30:00.000002Z',score:0,metricTie:true,unresolvedTie:true,overlappingTopCategories:['FIRST_COMPLETION','COMMENT_KING'],proofs:[{mission:'SAY_SOMETHING',sourceKind:'CHEER_REVISION',sourceId:7,receiptOrder:9,acceptedAt:'2026-01-01T00:30:00.000002Z',valid:true}]};
test('empty awards remain provisional, show missing settings and never manufacture winners',()=>{
 const html=render(base);assert.match(html,/잠정 후보 · 수상 확정 아님/);assert.match(html,/후보가 없어요/);assert.match(html,/미설정/);assert.doesNotMatch(html,/<button|당첨자|수상 확정하기/);
});
test('ties, real zeroes, exact timestamp and cross-category conflicts stay visible without assigning prizes',()=>{
 const html=render({...base,category:'COMMENT_KING',eligibleCompleteCount:1,total:1,items:[row]});
 assert.match(html,/0 개 소원/);assert.match(html,/공동 순위/);assert.match(html,/복수 부문 1위/);assert.match(html,/배정하지 않음/);assert.match(html,/000001Z/);
 assert.match(html,/YOU GOT THIS/);assert.match(html,/&lt;script&gt;fixture&lt;\/script&gt;/);assert.doesNotMatch(html,/<script>/);
});
test('bounded server pagination preserves category and review records do not expose game payloads',()=>{
 const html=render({...base,category:'FAST_TAP',total:41,offset:20,items:[{...row,score:140}],reviewTotal:2,reviewRecords:[{userId:9,sourceId:0,reason:'INVALID_SHOW_ME'},{userId:10,sourceId:22,reason:'TAP_REVIEW_REQUIRED'}]});
 assert.match(html,/category=FAST_TAP&amp;offset=0/);assert.match(html,/category=FAST_TAP&amp;offset=40/);assert.match(html,/완료 근거 불일치/);assert.match(html,/FAST TAP 검토 대기/);
 assert.doesNotMatch(html,/payload|비밀번호|mattermost/);
});
test('candidate categories contain only four confirmed competitions, with no random-draw endpoint',()=>{
 assert.deepEqual(Object.keys(awardLabels),['FIRST_COMPLETION','COMMENT_KING','POPULAR_CHEER','FAST_TAP']);
});
