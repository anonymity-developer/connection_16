/* global document, window */
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {chromium} from 'playwright';
import {preview} from 'vite';
const output=resolve('../output/playwright');await mkdir(output,{recursive:true});
const server=await preview({preview:{host:'127.0.0.1',port:4582,strictPort:true}});let browser;
try {
 browser=await chromium.launch();const page=await browser.newPage({viewport:{width:390,height:900}});
 let actor='participant',generation=0,allowed=true,mode='lost',savedReport=null,savedChange=null,reportWrites=0,changeWrites=0,active=true;
 const errors=[],routeErrors=[],checks=[],codes=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'],states=new Map(codes.map(code=>[code,'COMPLETED']));
 const now='2026-11-12T03:00:00Z',person={id:10,signupOrder:1,username:'support-fixture',nickname:'운영 지원 검사',realName:'가상 참가자',region:'서울',classNo:1,status:'ACTIVE',joinedAt:now,lastLoginAt:now,unlockedCount:5,completedCount:5,completionOrder:7,completedAt:now,connectionSeconds:60,lastSeenAt:now,activeSessions:1};
 const report={id:1,reporterId:10,username:person.username,nickname:person.nickname,body:'오류 검사 '.repeat(28),pagePath:'/missions/fast-tap',createdAt:now};
 const detail=()=>({member:{...person,status:active?'ACTIVE':'WITHDRAWN'},missions:codes.map(code=>({code,title:code,state:states.get(code),unlockedAt:now,completedAt:states.get(code)==='COMPLETED'?now:null,participated:true,resultStatus:null,tapCount:null,syncType:null,moderationStatus:null})),connections:[],bio:'검사 소개',interests:['백엔드'],githubUrl:null,blogUrl:null,resetGeneration:generation,resets:[]});
 await page.route('**/api/v1/**',async route=>{try{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.slice(7),method=req.method(),reply=(status,json)=>route.fulfill({status,json});
  if(path==='/users/me')return actor==='guest'?reply(401,{code:'UNAUTHENTICATED',message:'로그인이 필요해요.'}):reply(200,{id:actor==='admin'?1:10,username:actor==='admin'?'admin:fixture':person.username,nickname:person.nickname,admin:actor==='admin',onboardingRequired:false});
  if(path==='/auth/config')return reply(200,{available:true,participantAvailable:true,adminAvailable:true,passwordMinLength:8,passwordMaxLength:20});
  if(path==='/csrf')return reply(200,{headerName:'X-XSRF-TOKEN',token:'support-fixture-csrf'});
  if(path==='/tags/pending')return reply(200,{present:false});if(path==='/event')return reply(200,{status:'CLOSED'});
  if(path==='/missions/collection')return reply(200,{missions:codes.map(code=>({code,state:'LOCKED',active:true})),completedCount:0,completion:{complete:false},resetGeneration:0});
  if(path==='/admin/access')return reply(allowed?200:403,allowed?{admin:true,moderationEnabled:false}:{code:'ADMIN_REQUIRED',message:'관리자 권한이 필요해요.'});
  if(path==='/admin/members/10'&&method==='GET')return reply(200,detail());
  if(path==='/admin/error-reports')return reply(200,{items:[report],total:26,offset:Number(url.searchParams.get('offset')||0),nextOffset:url.searchParams.get('offset')==='25'?null:25});
  if(method!=='GET')assert.equal(req.headers()['x-xsrf-token'],'support-fixture-csrf');
  if(path==='/error-reports'){
   const data={key:req.headers()['idempotency-key'],body:req.postDataJSON()};assert.ok(data.key);assert.deepEqual(Object.keys(data.body).sort(),['body','pagePath']);
   if(savedReport){assert.deepEqual(data,savedReport);return reply(200,{id:1,createdAt:now});}
   savedReport=data;reportWrites++;return reply(503,{code:'NETWORK_ERROR',message:'응답을 확인하지 못했어요.'});
  }
  if(path.startsWith('/admin/members/10/')&&method==='POST'){
   const data={key:req.headers()['idempotency-key'],body:req.postDataJSON(),path};
   if(mode==='denied'){allowed=false;return reply(403,{code:'ADMIN_REQUIRED',message:'관리자 권한이 필요해요.'});}
   if(mode==='stale'){generation++;return reply(409,{code:'PARTICIPATION_RESET',message:'참여 상태가 바뀌었어요.'});}
   if(savedChange&&savedChange.key===data.key){assert.deepEqual(data,savedChange);return reply(200,{id:1,generation});}
   assert.equal(data.body.expectedGeneration,generation);savedChange=data;changeWrites++;generation++;
   if(data.body.action==='DEACTIVATE')active=false;else states.set(path.split('/').at(-1),data.body.action==='REVOKE'?'UNLOCKED':data.body.action==='LOCK'?'LOCKED':'COMPLETED');
   return mode==='lost'?reply(503,{code:'NETWORK_ERROR',message:'응답을 확인하지 못했어요.'}):reply(200,{id:1,generation});
  }
  throw Error(`Unexpected ${method} ${path}`);
 }catch(error){routeErrors.push(error.message);await route.fulfill({status:500,json:{message:'검사 계약 오류'}});}});
 page.on('pageerror',e=>errors.push(e.message));const visit=path=>page.goto(`http://127.0.0.1:4582${path}`),record=text=>{checks.push(text);console.log('PASS '+text);};
 await visit('/');await page.getByRole('button',{name:'오류 신고',exact:true}).click();const dialog=page.getByRole('dialog',{name:'오류 신고',exact:true});
 for(const width of [360,390,430,768,1440]) {await page.setViewportSize({width,height:900});const box=await dialog.boundingBox();assert.ok(box.x>=0&&box.x+box.width<=width);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));await page.screenshot({path:resolve(output,`support-report-${width}.png`)});}
 await page.getByLabel('오류 내용',{exact:true}).fill('😀'.repeat(201));assert.equal(Array.from(await page.getByLabel('오류 내용',{exact:true}).inputValue()).length,200);await page.getByText('200 / 200자',{exact:true}).waitFor();
 await page.getByRole('button',{name:'신고 보내기',exact:true}).click();await page.getByRole('button',{name:'같은 신고 다시 보내기',exact:true}).click();await page.getByText('오류 신고를 보냈어요.',{exact:true}).waitFor();assert.equal(reportWrites,1);assert.equal(savedReport.body.pagePath,'/');await page.getByRole('button',{name:'확인',exact:true}).click();
 record('200 Unicode characters, responsive header/dialog and lost-response retry keep one report without client-supplied identity');
 actor='guest';await visit('/');await page.getByRole('button',{name:'오류 신고',exact:true}).click();await dialog.getByRole('link',{name:'로그인하기',exact:true}).waitFor();assert.equal(await page.locator('textarea').count(),0);await page.keyboard.press('Escape');
 actor='admin';await visit('/admin/error-reports');await page.getByText(report.body,{exact:true}).waitFor();for(const width of [360,390,430,768,1440]){await page.setViewportSize({width,height:900});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));await page.screenshot({path:resolve(output,`support-admin-reports-${width}.png`),fullPage:true});}await page.getByRole('link',{name:'다음',exact:true}).click();await page.waitForURL('**offset=25');
 record('guest reporting requires login; administrator report inbox shows author, time, page and pagination at five widths');
 await visit('/admin/members/10');const row=page.locator('.member-control-row').filter({hasText:'SYNC_ME'});await row.getByRole('button',{name:'획득 취소·재참여',exact:true}).click();
 const changeDialog=page.getByRole('dialog');for(const width of [360,390,430,768,1440]){await page.setViewportSize({width,height:900});const box=await changeDialog.boundingBox();assert.ok(box.x>=0&&box.x+box.width<=width);await page.screenshot({path:resolve(output,`support-member-change-${width}.png`)});}
 await page.getByRole('button',{name:'변경 적용',exact:true}).click();assert.equal(changeWrites,0);await page.getByLabel('변경 사유',{exact:true}).fill('성향 게임 재참여 검사');await page.getByLabel('확인란에 ‘적용’를 입력해 주세요',{exact:true}).fill('적용');await page.getByRole('button',{name:'변경 적용',exact:true}).click();await page.getByRole('button',{name:'같은 요청 다시 보내기',exact:true}).click();await changeDialog.waitFor({state:'hidden'});await row.getByText('SYNC_ME · 해금',{exact:true}).waitFor();assert.equal(changeWrites,1);assert.equal(states.get('SAY_SOMETHING'),'COMPLETED');
 mode='stale';await row.getByRole('button',{name:'다시 잠금',exact:true}).click();await page.getByLabel('변경 사유',{exact:true}).fill('오래된 화면 검사');await page.getByLabel('확인란에 ‘적용’를 입력해 주세요',{exact:true}).fill('적용');await page.getByRole('button',{name:'변경 적용',exact:true}).click();await page.getByRole('button',{name:'현재 상태 다시 불러오기',exact:true}).click();await changeDialog.waitFor({state:'hidden'});
 mode='success';await page.getByRole('button',{name:'계정 비활성화',exact:true}).click();await page.getByLabel('변경 사유',{exact:true}).fill('테스트 계정 정리');await page.getByLabel('확인란에 ‘비활성화’를 입력해 주세요',{exact:true}).fill('비활성화');await page.getByRole('button',{name:'변경 적용',exact:true}).click();await page.getByText('비활성화된 계정의 이전 기록입니다. 같은 Mattermost 아이디로 다시 회원가입할 수 있어요.',{exact:true}).waitFor();assert.equal(await page.locator('.member-control-row').count(),0);
 record('member changes require reason/confirmation, retry the same request, preserve other missions and handle stale generation and deactivation');
 active=true;mode='denied';await visit('/admin/members/10');await page.locator('.member-control-row').first().getByRole('button',{name:'다시 잠금',exact:true}).click();await page.getByLabel('변경 사유',{exact:true}).fill('권한 회수');await page.getByLabel('확인란에 ‘적용’를 입력해 주세요',{exact:true}).fill('적용');await page.getByRole('button',{name:'변경 적용',exact:true}).click();await page.getByRole('alert').getByText('관리자 권한이 필요해요.',{exact:true}).waitFor();assert.equal(await page.locator('.member-control-row').count(),0);
 assert.deepEqual(errors,[]);assert.deepEqual(routeErrors,[]);record('revoked administrator access removes participant details and controls');await writeFile(resolve(output,'support-checks.json'),JSON.stringify({kind:'isolated browser API fixtures only',checks,errors,routeErrors},null,2));
}finally{await browser?.close();await server.httpServer.close();}
