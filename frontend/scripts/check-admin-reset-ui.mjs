/* global document, window */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Explicit browser API fixtures only. No real accounts, resets, submissions or moderation calls.
const output=resolve('../output/playwright');await mkdir(output,{recursive:true});
const server=await preview({preview:{host:'127.0.0.1',port:4578,strictPort:true}});let browser;
try {
  browser=await chromium.launch();const page=await browser.newPage({viewport:{width:390,height:900}});
  const errors=[],routeErrors=[],checks=[],writes=[];await page.addLocatorHandler(page.locator('.mission-earned-actions'), async () => { await page.getByRole('button', {name:'계속 참여하기',exact:true}).click(); });
  page.on('pageerror',e=>errors.push(e.message));
  const codes=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'];const now='2026-11-11T04:00:00Z';
  let generation=0,participated=true,actor='admin',allowed=true,resetMode='lost',resetOperations=0,moderationReads=0;
  let savedReset=null,lastReset=null;const history=[];
  const member={id:10,signupOrder:1,username:'fixture-reset',nickname:'초기화 검증 참가자',realName:'가상 참가자',region:'서울',classNo:1,status:'ACTIVE',joinedAt:now,lastLoginAt:now,unlockedCount:5,completedCount:5,completionOrder:1,completedAt:now,connectionSeconds:60,lastSeenAt:now,activeSessions:1};
  const blank=()=>({id:null,version:0,visibility:null,approvedRevision:null,pendingRevision:null,latestRevision:null,missionCompleted:false,luckScore:null,landingZone:null});
  const slots={wishes:blank(),cheers:blank()},comments=[];
  function detail(){return {member:{...member,completedCount:participated?5:1,completionOrder:participated?1:null,completedAt:participated?now:null},missions:codes.map(code=>({code,title:code,state:code==='SHOW_ME'||participated?'COMPLETED':'UNLOCKED',unlockedAt:now,completedAt:code==='SHOW_ME'||participated?now:null,participated:code==='SHOW_ME'||participated,resultStatus:code==='FAST_TAP'&&participated?'VALID':null,tapCount:code==='FAST_TAP'&&participated?118:null,syncType:null,moderationStatus:null})),connections:[],bio:'가상 프로필 소개',interests:['백엔드'],githubUrl:null,blogUrl:null,resetGeneration:generation,resets:history};}
  await page.route('**/api/v1/**',async route=>{
    try {
      const req=route.request(),url=new URL(req.url()),path=url.pathname.slice('/api/v1'.length),method=req.method();
      const reply=(status,value)=>route.fulfill({status,json:value});
      if(path==='/auth/config')return reply(200,{available:true,participantAvailable:true,adminAvailable:true,passwordMinLength:15,passwordMaxLength:64});
      if(path==='/users/me')return reply(200,{id:actor==='admin'?1:10,username:actor==='admin'?'admin:fixture':'fixture-reset',nickname:actor==='admin'?null:'가상 참가자',onboardingRequired:false,admin:actor==='admin'});
      if(path==='/tags/pending')return reply(200,{present:false});
      if(path==='/csrf')return reply(200,{headerName:'X-XSRF-TOKEN',token:'reset-test-csrf'});
      if(method!=='GET'){assert.equal(req.headers()['x-xsrf-token'],'reset-test-csrf');writes.push(path);}
      if(path==='/admin/access')return allowed?reply(200,{admin:true,moderationEnabled:false}):reply(403,{code:'ADMIN_REQUIRED',message:'관리자 권한이 필요해요.'});
      if(path==='/admin/members/10'&&method==='GET')return reply(200,detail());
      if(path==='/admin/members/10/reset-games') {
        const body=req.postDataJSON(),key=req.headers()['idempotency-key'];assert.match(key,/^[A-Za-z0-9_-]{16,64}$/);assert.equal(body.confirmation,'RESET');
        if(resetMode==='stale'){generation++;participated=false;return reply(409,{code:'PARTICIPATION_RESET',message:'참여 상태가 바뀌었어요. 다시 불러와 주세요.'});}
        if(resetMode==='forbidden'){allowed=false;return reply(403,{code:'ADMIN_REQUIRED',message:'관리자 권한이 필요해요.'});}
        if(lastReset){assert.equal(key,lastReset.key);assert.deepEqual(body,lastReset.body);return reply(200,savedReset);}
        assert.equal(body.expectedGeneration,generation);generation++;participated=false;resetOperations++;
        savedReset={id:1,generation,actorId:1,reason:body.reason,createdAt:now,summary:{content:3,tapAttempts:1,sync:1,tagsPreserved:true,showMePreserved:true}};history.push(savedReset);
        lastReset={key,body};
        return resetMode==='lost'?reply(503,{code:'UNKNOWN_RESPONSE',message:'테스트 응답 유실'}):reply(200,savedReset);
      }
      if(path==='/admin/content'||path==='/admin/audit-logs')return reply(200,{items:[],nextCursor:null});
      if(path.startsWith('/admin/moderation')){moderationReads++;throw new Error('Disabled moderation must not be loaded');}
      if(path==='/event')return reply(200,{status:'OPEN',opensAt:null,submitClosesAt:null,profilePreparationEnabled:false});
      if(path==='/missions/collection')return reply(200,{missions:codes.map((code,index)=>({id:index+1,code,state:code==='SHOW_ME'||slots[code==='MAKE_A_WISH'?'wishes':'cheers'].missionCompleted&&['MAKE_A_WISH','SAY_SOMETHING'].includes(code)?'COMPLETED':'UNLOCKED',active:true})),completedCount:1+Number(slots.wishes.missionCompleted)+Number(slots.cheers.missionCompleted),completion:{complete:false,completionOrder:null,completedAt:null,awardConfirmed:false},resetGeneration:generation});
      if(path==='/tap/me')return reply(200,{officialAvailable:false,attempt:{attemptId:1,generation:1,status:'INVALID',serverNow:now,startsAt:now,endsAt:now,submitDeadline:now,tapCount:118,reviewReason:'DUPLICATE_MILLISECOND',provisional:false}});
      if(path==='/tap/leaderboard')return reply(200,{top:[],own:null});
      if(path==='/wishes/40/comments/me')return reply(200,{items:comments,nextCursor:null});
      if(path==='/wishes/40/comments'){
        if(method==='POST'){assert.equal(req.headers()['x-participation-generation'],String(generation));const body=req.postDataJSON();const row={id:50,content:body.content,revealProfile:body.revealProfile,status:'APPROVED',reason:null,submittedAt:now,requestKey:req.headers()['idempotency-key']};comments.push(row);return reply(202,row);}
        return reply(200,{items:comments.map(row=>({id:row.id,content:row.content,revealProfile:false,nickname:null,profileId:null})),nextCursor:null});
      }
      if(path==='/cheers/likes')return reply(200,{});
      const kind=path.startsWith('/wishes')?'wishes':path.startsWith('/cheers')?'cheers':null;
      if(kind){
        if(method==='GET'&&path.endsWith('/me'))return reply(200,slots[kind]);
        if(method==='GET')return reply(200,{items:slots[kind].approvedRevision?[{...slots[kind].approvedRevision,id:slots[kind].id,nickname:null,profileId:null,luckScore:null,landingZone:null}]:[],nextCursor:null});
        assert.equal(req.headers()['x-participation-generation'],String(generation));const body=req.postDataJSON(),id=kind==='wishes'?40:41;
        const revision={id:100+id,itemId:id,number:1,...body,status:'APPROVED',reason:null,submittedAt:now};slots[kind]={...blank(),id,version:1,visibility:'VISIBLE',approvedRevision:revision,latestRevision:revision,missionCompleted:true};return reply(202,slots[kind]);
      }
      throw new Error(`Unexpected ${method} ${path}`);
    }catch(error){routeErrors.push(error.message);await route.fulfill({status:500,json:{message:'테스트 계약 오류'}});}
  });
  const visit=path=>page.goto('http://127.0.0.1:4578'+path),record=name=>{checks.push(name);console.log('PASS '+name);};
  await visit('/admin/members/10');await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).waitFor();
  for(const width of [360,390,430,768,1440]){
    await page.setViewportSize({width,height:900});await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).click();
    await page.getByRole('dialog',{name:'게임 참여 초기화',exact:true}).waitFor();
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
    await page.screenshot({path:resolve(output,`admin-game-reset-${width}.png`),fullPage:true});
    await page.keyboard.press('Escape');assert.equal(await page.getByRole('dialog').count(),0);
    assert.equal(await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).evaluate(el=>el===document.activeElement),true);
  }
  record('reset dialog fits five widths and Escape restores focus without a write');
  await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).click();await page.getByRole('button',{name:'게임 참여 초기화 실행',exact:true}).click();
  await page.getByRole('alert').getByText('확인란에 초기화를 입력해 주세요.',{exact:true}).waitFor();assert.equal(resetOperations,0);
  await page.getByLabel('확인란에 ‘초기화’를 입력해 주세요',{exact:true}).fill('초기화');
  await page.getByRole('button',{name:'게임 참여 초기화 실행',exact:true}).dblclick();
  await page.getByRole('button',{name:'같은 초기화 다시 요청',exact:true}).waitFor();assert.equal(resetOperations,1);
  assert.equal(await page.getByLabel('초기화 사유',{exact:true}).isDisabled(),true);
  await page.getByRole('button',{name:'같은 초기화 다시 요청',exact:true}).click();await page.getByText('게임 참여를 초기화했어요.',{exact:false}).waitFor();
  await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).waitFor({state:'visible'});
  await page.waitForFunction(()=>document.querySelector('.admin-reset-panel button')?.disabled);
  await page.getByText('초기화 이력 · 최근 1건',{exact:true}).click();await page.getByText('1번째 초기화',{exact:true}).waitFor();
  assert.equal(writes.filter(p=>p.endsWith('/reset-games')).length,2);record('confirmation, duplicate click and a lost response retry produce one reset and show retained history');
  participated=true;lastReset=null;resetMode='stale';await visit('/admin/members/10');await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).click();await page.getByLabel('확인란에 ‘초기화’를 입력해 주세요',{exact:true}).fill('초기화');await page.getByRole('button',{name:'게임 참여 초기화 실행',exact:true}).click();await page.getByRole('button',{name:'참여 상태 다시 불러오기',exact:true}).click();assert.equal(resetOperations,1);record('a stale reset is blocked and must reload current participation');
  await visit('/admin/moderation');await page.getByRole('heading',{name:'등록된 콘텐츠',exact:true}).waitFor();assert.equal(await page.getByRole('link',{name:'검수 대기',exact:true}).count(),0);assert.equal(moderationReads,0);record('default admin content management never loads a moderation queue');
  actor='participant';await visit('/missions/make-a-wish');await page.locator('#content-text').fill('즉시 공개되는 검증 소원');await page.getByRole('button',{name:'동전 던지기',exact:true}).click();await page.getByText('공개된 글',{exact:true}).waitFor();await page.getByRole('button',{name:'소원 1 열기',exact:true}).click();await page.getByRole('button',{name:'댓글 보기',exact:true}).click();await page.getByLabel('동료의 소원에 댓글 남기기',{exact:true}).fill('바로 공개되는 검증 댓글');await page.getByRole('button',{name:'댓글 남기기',exact:true}).click();await page.locator('.comment-list').first().getByText('바로 공개되는 검증 댓글',{exact:true}).waitFor();
  await visit('/missions/say-something');await page.locator('#content-text').fill('즉시 공개되는 검증 응원');await page.getByRole('button',{name:'응원 보내기',exact:true}).click();await page.getByText('공개된 글',{exact:true}).waitFor();record('wish, cheer and comment submissions become public immediately using the current reset generation');
  await visit('/missions/fast-tap');await page.getByRole('heading',{name:'기록이 자동 무효 처리됐어요',exact:true}).waitFor();assert.equal(await page.getByText('F를 획득했어요!',{exact:true}).count(),0);assert.equal(await page.getByText('검토 대기',{exact:false}).count(),0);record('duplicate-millisecond tap records show automatic invalidation without a manual-review claim');
  actor='admin';participated=true;resetMode='forbidden';await visit('/admin/members/10');await page.getByRole('button',{name:'게임 참여 초기화',exact:true}).click();await page.getByLabel('확인란에 ‘초기화’를 입력해 주세요',{exact:true}).fill('초기화');await page.getByRole('button',{name:'게임 참여 초기화 실행',exact:true}).click();await page.getByRole('alert').getByText('관리자 권한이 필요해요.',{exact:true}).waitFor();assert.equal(await page.locator('.admin-reset-panel').count(),0);record('revoked reset permission immediately removes private member details');
  assert.deepEqual(errors,[]);assert.deepEqual(routeErrors,[]);await writeFile(resolve(output,'direct-publishing-reset-checks.json'),JSON.stringify({kind:'synthetic browser contracts only; no real resets or participant records',checks,writes,resetOperations,errors,routeErrors},null,2));
}finally{await browser?.close();await server.httpServer.close();}
