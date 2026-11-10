/* global window, document */
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { preview } from 'vite';
import { emptyReport, reviewReport } from '../tests/fixtures/post-event.mjs';
const server=await preview({preview:{host:'127.0.0.1',port:4481,strictPort:true}});let browser;
try{
 browser=await chromium.launch();const page=await browser.newPage();let admin=false,privateReads=0,denyResults=false,exportChanged=false;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{
  const request=route.request(),url=new URL(request.url());if(url.origin!=='http://127.0.0.1:4481')return route.abort();
  if(!url.pathname.startsWith('/api/v1/'))return route.continue();assert.equal(request.method(),'GET','result workspace must not mutate data');
  const path=url.pathname.slice(7),reply=(status,json)=>route.fulfill({status,json});
  if(path==='/auth/config')return reply(200,{available:false,passwordMinLength:15,passwordMaxLength:64});
  if(path==='/users/me')return reply(200,{id:1,username:'fixture',nickname:'fixture',onboardingRequired:false});
  if(path==='/event')return reply(200,{status:'CLOSED'});if(path==='/missions/collection')return reply(200,{missions:[],completedCount:0,completion:{complete:false}});if(path==='/tags/pending')return reply(200,{present:false});
  if(path==='/admin/access')return reply(admin?200:403,admin?{admin:true}:{code:'ADMIN_REQUIRED',message:'권한 없음'});
  if(path==='/admin/results'){privateReads++;assert.equal(admin,true);return reply(denyResults?403:200,denyResults?{code:'ADMIN_REQUIRED',message:'권한이 변경됐어요.'}:reviewReport);}
  if(path==='/admin/results/export'){if(exportChanged)return reply(409,{code:'RESULT_CHANGED',message:'집계가 바뀌었어요.'});assert.equal(url.searchParams.get('snapshot'),reviewReport.snapshotId);return route.fulfill({status:200,headers:{'Content-Type':'text/csv;charset=UTF-8','Content-Disposition':'attachment; filename=tagme-post-event.csv','Cache-Control':'no-store'},body:'section,key\r\nSUMMARY,fixture\r\n'});}
  if(path.startsWith('/admin/results/participants/')){assert.equal(url.searchParams.get('purpose'),'RESULT_RECONCILIATION');return reply(200,{participantId:'P-7',observedAt:emptyReport.data.window.observedAt,purpose:'RESULT_RECONCILIATION',profileFirstPublishedAt:null,missions:[],receipts:[],nextCursor:null,limitation:'제한 조회 fixture'});}
  return reply(503,{code:'FIXTURE_ONLY',message:'범위 밖 fixture'});
 });
 await page.goto('http://127.0.0.1:4481/admin?tab=results');await page.getByRole('alert').waitFor();assert.equal(privateReads,0);admin=true;
 for(const width of [360,375,390,412,430,1440]){await page.setViewportSize({width,height:900});await page.goto('http://127.0.0.1:4481/admin?tab=results');await page.getByRole('heading',{name:'행사 결과 · 잠정 검토',exact:true}).waitFor();await page.getByRole('button',{name:'CSV 내려받기',exact:true}).waitFor();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));assert.equal(await page.getByRole('button',{name:/확정하기|추첨하기|삭제하기/}).count(),0);}
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'CSV 내려받기',exact:true}).click();assert.equal((await download).suggestedFilename(),'tagme-post-event.csv');
 await page.getByLabel('내부 참가자 번호 (P- 뒤 숫자)',{exact:true}).fill('7');await page.getByRole('button',{name:'결과 대조 목적으로 조회',exact:true}).click();await page.getByText('제한 조회 fixture',{exact:true}).waitFor();
 exportChanged=true;await page.getByRole('button',{name:'CSV 내려받기',exact:true}).click();await page.getByRole('alert').filter({hasText:'집계가 바뀌었어요.'}).waitFor();
 denyResults=true;await page.getByRole('button',{name:'결과 다시 조회',exact:true}).click();await page.getByRole('alert').filter({hasText:'권한이 변경됐어요.'}).waitFor();assert.equal(await page.getByRole('button',{name:'CSV 내려받기',exact:true}).count(),0);
 assert.deepEqual(errors,[]);console.log('PASS: GET-only result fixtures; 6 viewport widths; permission, limited history, download, changed snapshot and revoked-role cleanup. Not a live DB E2E.');
}finally{await browser?.close();await new Promise(resolve=>server.httpServer.close(resolve));}
