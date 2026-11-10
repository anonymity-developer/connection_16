/* global document, window */
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { preview } from 'vite';
const server=await preview({preview:{host:'127.0.0.1',port:4479,strictPort:true}});let browser;
try{
 browser=await chromium.launch();const page=await browser.newPage();let admin=false,reads=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{
  const request=route.request(),url=new URL(request.url());if(url.origin!=='http://127.0.0.1:4479')return route.abort();
  if(!url.pathname.startsWith('/api/v1/'))return route.continue();
  const path=url.pathname.slice(7);assert.equal(request.method(),'GET','candidate UI is read-only');
  const reply=(status,json)=>route.fulfill({status,json});
  if(path==='/auth/config')return reply(200,{available:false,passwordMinLength:15,passwordMaxLength:64});
  if(path==='/users/me')return reply(200,{id:1,username:'fixture',nickname:'검토용',onboardingRequired:false});
  if(path==='/event')return reply(200,{status:'CLOSED'});
  if(path==='/missions/collection')return reply(200,{missions:[],completedCount:0,completion:{complete:false}});
  if(path==='/tags/pending')return reply(200,{present:false});
  if(path==='/admin/access')return reply(admin?200:403,admin?{admin:true}:{code:'ADMIN_REQUIRED',message:'권한이 없어요.'});
  if(path==='/admin/awards'){
   reads++;assert.equal(admin,true);return reply(200,{ruleVersion:'fixture-v1',provisional:true,observedAt:'2026-01-01T01:00:00Z',opensAt:null,submitClosesAt:null,moderationClosesAt:null,eventPhase:'NOT_CONFIGURED',category:url.searchParams.get('category'),eligibleCompleteCount:0,total:0,offset:0,limit:20,items:[],invalidProofs:0,incompleteProofSets:0,pendingModeration:0,reviewTotal:0,reviewRecords:[],pendingDecisions:['랜덤 추첨 미확정']});
  }
  return reply(503,{code:'TEST_OUT_OF_SCOPE',message:'범위 밖 fixture'});
 });
 await page.goto('http://127.0.0.1:4479/admin/moderation?tab=awards');await page.getByRole('alert').waitFor();assert.equal(reads,0,'no private candidate request before permission');admin=true;
 for(const width of [360,375,390,412,430,1440]){
  await page.setViewportSize({width,height:900});await page.goto('http://127.0.0.1:4479/admin/moderation?tab=awards');await page.getByRole('heading',{name:'잠정 시상 후보',exact:true}).waitFor();
  for(const category of ['FIRST_COMPLETION','COMMENT_KING','POPULAR_CHEER','FAST_TAP']){await page.getByLabel('시상 부문',{exact:true}).selectOption(category);await page.getByText('이 조회 범위의 후보가 없어요. 설정과 다섯 미션의 유효 근거를 확인해 주세요.',{exact:true}).waitFor();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));}
 }
 assert.deepEqual(errors,[]);console.log('PASS: candidate permission gate, GET-only fixtures, four categories, 360/375/390/412/430/1440 widths');
}finally{await browser?.close();await new Promise(resolve=>server.httpServer.close(resolve));}
