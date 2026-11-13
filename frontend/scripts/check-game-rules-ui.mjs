/* global document, window */
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Isolated production-build contracts. No real login, participant writes or tag tokens.
const output=resolve('../output/playwright');await mkdir(output,{recursive:true});
const server=await preview({preview:{host:'127.0.0.1',port:4279,strictPort:true}});let browser;
try {
  browser=await chromium.launch();const page=await browser.newPage({viewport:{width:390,height:900}});
  const errors=[],writes=[],codes=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'];
  const slugs=['show-me','sync-me','make-a-wish','fast-tap','say-something'];
  const now='2026-11-12T01:00:00Z',blank=()=>({id:null,version:0,visibility:null,approvedRevision:null,pendingRevision:null,latestRevision:null,missionCompleted:false,luckScore:null,landingZone:null});
  const slots={wishes:blank(),cheers:blank()};
  for(const [kind,id] of [['wishes',40],['cheers',41]]) {
    const revision={id:100+id,itemId:id,number:1,status:'APPROVED',reason:null,submittedAt:now,content:'이전에 작성한 내 글',templateId:'PLAIN',style:'ROSE',anonymous:true};
    slots[kind]={...blank(),id,version:1,visibility:'VISIBLE',approvedRevision:revision,latestRevision:revision,missionCompleted:true};
  }
  let editing=false,locked=false,rankingReads=0,saveMode='lost',savedRequest=null;
  const top=Array.from({length:10},(_,i)=>({rank:i+1,profileId:i+81,nickname:i===0?'<img src=x onerror=alert(1)>':`검사 동료 ${i+1}`,tapCount:200-i,submittedAt:now,message:i===0?'서버에서 받은 소감':null}));
  let board={top,own:{attemptId:73,rank:12,tapCount:100,recordBreaker:true,canWriteMessage:true,message:null}};
  page.on('pageerror',e=>errors.push(e.message));
  await page.addLocatorHandler(page.locator('.mission-earned-actions'),()=>page.getByRole('button',{name:'계속 참여하기',exact:true}).click());
  await page.route('**/api/v1/**',async route=>{
    const req=route.request(),path=new URL(req.url()).pathname.slice(7),method=req.method(),reply=(status,json)=>route.fulfill({status,json});
    try {
      if(path==='/auth/config')return reply(200,{available:true,passwordMinLength:8,passwordMaxLength:20});
      if(path==='/users/me')return reply(200,{id:73,username:'rules-fixture',nickname:'내 프로필 닉네임',onboardingRequired:false});
      if(path==='/tags/pending')return reply(200,{present:false});
      if(path==='/csrf')return reply(200,{headerName:'X-XSRF-TOKEN',token:'rules-csrf'});
      if(path==='/event')return reply(200,{status:'OPEN',opensAt:null,submitClosesAt:null,profilePreparationEnabled:false});
      if(path==='/missions/collection')return reply(200,{missions:codes.map((code,i)=>({id:i+1,code,active:true,state:locked?'LOCKED':editing&&[2,4].includes(i)&&!slots[i===2?'wishes':'cheers'].id?'UNLOCKED':'COMPLETED'})),completedCount:locked?0:5,completion:{complete:!locked,completionOrder:1,completedAt:now,awardConfirmed:false},resetGeneration:2});
      if(path==='/users/me/profile')return reply(200,{identity:{realName:'가상 참가자',region:'서울',classNo:1,photoUrl:null,ready:true},profile:{nickname:'내 프로필 닉네임',bio:'프로필에 저장한 한 줄 소개',interestIds:[1],githubUrl:null,blogUrl:null},consents:{ready:true,policies:[]},profileComplete:true,participationReady:true});
      if(path==='/interests')return reply(200,[{id:1,name:'백엔드',category:'DOMAIN'}]);
      if(path==='/sync/me')return reply(200,{result:{questionnaireVersion:'fixture-v1',type:{code:'T',title:'검사 성향',description:'서버가 확정한 성향'},axisScores:{},submittedAt:now}});
      if(path==='/discovery/people')return reply(200,{items:[],nextCursor:null});
      if(path==='/tap/me')return reply(200,{officialAvailable:false,attempt:{attemptId:73,generation:1,status:'VALID',serverNow:now,startsAt:now,endsAt:now,submitDeadline:now,tapCount:100,reviewReason:null,provisional:false}});
      if(path==='/tap/leaderboard'){rankingReads++;return reply(200,board);}
      if(path==='/tap/record-message') {
        assert.equal(method,'POST');assert.equal(req.headers()['x-xsrf-token'],'rules-csrf');assert.equal(req.headers()['x-participation-generation'],'2');
        const request={key:req.headers()['idempotency-key'],body:req.postDataJSON()};assert.match(request.key,/^[A-Za-z0-9_-]{16,64}$/);
        assert.deepEqual(Object.keys(request.body).sort(),['attemptId','content']);assert.equal(request.body.attemptId,73);writes.push(request);
        if(savedRequest)assert.deepEqual(request,savedRequest);savedRequest=request;
        if(saveMode==='lost'){saveMode='ok';return reply(503,{message:'응답을 확인하지 못했어요.'});}
        if(saveMode==='conflict')return reply(409,{message:'참여 상태가 바뀌었어요.'});
        if(saveMode==='forbidden')return reply(403,{message:'접근 권한이 없어요.'});
        board={...board,own:{...board.own,canWriteMessage:false,message:request.body.content}};return reply(200,board);
      }
      if(path==='/cheers/likes')return reply(200,{});
      if(path==='/missions/show-me/reconcile')return reply(200,{newlyCompleted:false});
      const kind=path.startsWith('/wishes')?'wishes':path.startsWith('/cheers')?'cheers':null;
      if(kind) {
        if(method==='GET'&&path.endsWith('/me'))return reply(200,slots[kind]);
        if(method==='GET'&&path.includes('/comments'))return reply(200,{items:[],nextCursor:null});
        if(method==='GET')return reply(200,{items:slots[kind].approvedRevision?[{...slots[kind].approvedRevision,id:slots[kind].id,nickname:slots[kind].approvedRevision.anonymous?null:'공개한 동료',profileId:slots[kind].approvedRevision.anonymous?null:81,luckScore:null,landingZone:null}]:[],nextCursor:null});
        assert.equal(req.headers()['x-xsrf-token'],'rules-csrf');assert.equal(req.headers()['x-participation-generation'],'2');
        const body=req.postDataJSON();assert.equal(body.content.length,400);const previous=slots[kind];
        assert.equal(body.expectedVersion,previous.id?previous.version:null);
        const revision={...body,id:100+previous.version,itemId:kind==='wishes'?40:41,number:previous.version+1,status:'APPROVED',reason:null,submittedAt:now};
        slots[kind]={...blank(),id:revision.itemId,version:revision.number,visibility:'VISIBLE',approvedRevision:revision,latestRevision:revision,missionCompleted:true};return reply(202,slots[kind]);
      }
      throw new Error(`Unexpected ${method} ${path}`);
    }catch(error){errors.push(error.message);return reply(500,{message:'계약 검사 실패'});}
  });
  const visit=slug=>page.goto(`http://127.0.0.1:4279/missions/${slug}`);
  async function checkProgressRow() {
    const letter=await page.locator('.mission-reward .letter-sticker').boundingBox();
    const badge=await page.locator('.mission-reward .state-badge').boundingBox();
    assert.ok(letter&&badge&&badge.x>=letter.x+letter.width,'status is beside the letter');
    assert.ok(badge.y<letter.y+letter.height&&badge.y+badge.height>letter.y,'letter and status share a row');
    assert.equal(await page.locator('.mission-progress-summary > .state-badge').count(),0);
    const summary=page.locator('.mission-hint summary');
    if(!await page.locator('.mission-hint details').evaluate(el=>el.open))await summary.click();
    await page.locator('.mission-tip-note').getByRole('heading',{name:'TIP',exact:true}).waitFor();
    assert.ok((await page.locator('.mission-tip-note p').innerText()).length>0);
    const progressBox=await page.locator('.mission-progress-summary').boundingBox(),hintBox=await page.locator('.mission-hint').boundingBox();
    assert.ok(progressBox&&hintBox&&hintBox.y>=progressBox.y+progressBox.height-1,'mission guide is below progress at every width');
    assert.ok(Math.abs(hintBox.x-progressBox.x)<1&&Math.abs(hintBox.width-progressBox.width)<1,'mission guide uses the full card width');
  }
  for(const width of [360,390,430,640,641,768,1099,1100,1440]) {
    await page.setViewportSize({width,height:900});
    for(const slug of slugs) {
      await visit(slug);await page.locator('.mission-progress-summary').getByText('글자 획득 완료',{exact:true}).waitFor();
      if(slug==='show-me')await page.locator('.show-me-record-name').getByText('내 프로필 닉네임',{exact:true}).waitFor();
      if(slug==='fast-tap') {
        await page.locator('.tap-ranking-list li').last().waitFor();
      }
      await checkProgressRow();
      if(slug==='fast-tap')await page.getByText('19층과 20층 사이 계단에서 F를 찾아 NFC 태그를 읽어주세요.',{exact:true}).waitFor();
      assert.equal(await page.getByText('글자 획득 완료',{exact:true}).count(),1);
      assert.equal(await page.getByText('획득한 글자예요.',{exact:true}).count(),0);
      assert.equal(await page.locator('.mission-detail-heading .state-badge,.mission-complete-artwork').count(),0);
      assert.equal(await page.locator('.mission-detail-heading .detail-intro').count(),1);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false,`${slug} at ${width}`);
      if(['show-me','fast-tap'].includes(slug))await page.screenshot({path:resolve(output,`rules-${slug}-${width}.png`),fullPage:true});
    }
  }
  console.log('PASS all five completed missions show one progress badge; SHOW ME restores profile; nine widths fit');
  editing=true;slots.wishes=blank();slots.cheers=blank();await page.setViewportSize({width:390,height:900});
  for(const [kind,slug] of [['wishes','make-a-wish'],['cheers','say-something']]) {
    await visit(slug);const input=page.locator('#content-text'),anonymous=page.getByRole('checkbox',{name:'익명으로 남기기',exact:true});
    await input.waitFor();assert.equal(await input.getAttribute('maxlength'),'400');assert.equal(await anonymous.isChecked(),true);
    await input.fill('가'.repeat(400));await anonymous.uncheck();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);
    await page.screenshot({path:resolve(output,`rules-${kind}-400-390.png`),fullPage:true});
    await page.getByRole('button',{name:kind==='wishes'?'동전 던지기':'응원 보내기',exact:true}).click();
    await page.getByRole('button',{name:'같은 글 수정하기',exact:true}).waitFor();assert.equal(slots[kind].latestRevision.anonymous,false);
    if(kind==='wishes'){await page.getByRole('button',{name:'소원 1 열기',exact:true}).click();await page.getByRole('link',{name:'공개한 동료의 소원',exact:true}).waitFor();}
    await page.getByRole('button',{name:'같은 글 수정하기',exact:true}).click();assert.equal(await input.inputValue(),'가'.repeat(400));assert.equal(await anonymous.isChecked(),false);
    await anonymous.check();await page.getByRole('button',{name:'수정 내용 보내기',exact:true}).click();await page.getByRole('button',{name:'같은 글 수정하기',exact:true}).waitFor();
    assert.equal(slots[kind].latestRevision.anonymous,true);assert.equal(slots[kind].latestRevision.number,2);
    assert.equal(await page.locator('a[href="/discovery/profiles/81"]').count(),0);
  }
  console.log('PASS 400-character wish/cheer edits preserve a single slot and explicitly toggle public profile links');
  await visit('fast-tap');await page.locator('.tap-ranking-list li').last().waitFor();
  assert.equal(await page.locator('.tap-ranking-list li').count(),10);assert.equal(await page.locator('.tap-ranking-list a').first().getAttribute('href'),'/discovery/profiles/81');await page.getByText('12위',{exact:true}).waitFor();
  assert.equal(await page.locator('.tap-ranking-list img,.tap-ranking-list script').count(),0);
  const message=page.getByLabel('순위권 한마디',{exact:true});assert.equal(await message.getAttribute('maxlength'),'50');
  await message.fill('내 소감'.repeat(10));await page.getByRole('button',{name:'소감 남기기',exact:true}).click();await page.getByRole('button',{name:'같은 소감 다시 보내기',exact:true}).waitFor();assert.equal(await message.isDisabled(),true);
  await page.getByRole('button',{name:'같은 소감 다시 보내기',exact:true}).click();await page.locator('.tap-own-message').waitFor();assert.equal(writes.length,2);assert.equal(await message.count(),0);
  await page.reload();await page.locator('.tap-own-message').waitFor();assert.equal(await message.count(),0);
  console.log('PASS server top ten/own rank, escaped nicknames, retained record entitlement and identical one-time message retry');
  for(const mode of ['conflict','forbidden']) {
    board={top,own:{...board.own,message:null,canWriteMessage:true}};savedRequest=null;saveMode=mode;await page.reload();await message.fill('변경 경쟁 검사');
    await page.getByRole('button',{name:'소감 남기기',exact:true}).click();await page.getByRole('alert').waitFor();
    if(mode==='conflict'){assert.equal(await page.getByRole('button',{name:'소감 남기기',exact:true}).isDisabled(),true);await page.getByRole('button',{name:'순위 새로고침',exact:true}).click();await message.waitFor();assert.equal(await message.isEnabled(),true);}
    else {assert.equal(await page.locator('.tap-ranking-list li').count(),0);assert.equal(await message.count(),0);}
  }
  locked=true;const reads=rankingReads;
  for(const width of [360,390,430,640,641,768,1099,1100,1440]) {
    await page.setViewportSize({width,height:900});
    for(const slug of slugs) {
      await visit(slug);await page.locator('.mission-reward .state-badge').waitFor();await checkProgressRow();
      assert.equal(await page.getByText('글자 획득 완료',{exact:true}).count(),0);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);
    }
  }
  assert.equal(rankingReads,reads);
  console.log('PASS all five locked/completed missions place status beside the letter and retain TIP at nine widths');
  await page.clock.install({time:new Date('2026-11-12T03:00:00Z')});await page.clock.pauseAt(new Date('2026-11-12T03:00:00Z'));
  await page.mouse.move(0,0);await page.goto('http://127.0.0.1:4279/');
  const hint=page.getByRole('region',{name:'전체 미션 힌트',exact:true}),copy=hint.locator('p');await hint.waitFor();
  assert.equal(await page.getByRole('button',{name:'다른 응원',exact:true}).count(),0);
  const messages=['미션을 열심히 하는 자에게 행운이!','친구들의 프로필을 탐색하며 친밀감을 쌓아봐!','참여 자체가 행운이 될 수도 있어.'];
  const height=(await hint.boundingBox()).height;
  assert.equal(await copy.innerText(),messages[0]);await page.clock.runFor(2999);assert.equal(await copy.innerText(),messages[0]);
  await page.clock.runFor(1);assert.equal(await copy.innerText(),messages[1]);
  await page.clock.runFor(3000);assert.equal(await copy.innerText(),messages[2]);
  await page.clock.runFor(3000);assert.equal(await copy.innerText(),messages[0]);assert.equal((await hint.boundingBox()).height,height);
  await hint.hover();await page.clock.runFor(6000);assert.equal(await copy.innerText(),messages[0]);
  await page.mouse.move(0,0);await page.clock.runFor(3000);assert.equal(await copy.innerText(),messages[1]);
  await hint.focus();await page.clock.runFor(6000);assert.equal(await copy.innerText(),messages[1]);
  await page.getByRole('link',{name:'TAG ME 홈',exact:true}).focus();await page.clock.runFor(3000);assert.equal(await copy.innerText(),messages[2]);
  console.log('PASS home hints rotate every three seconds, loop, keep their height and pause for reading without another-message button');
  assert.deepEqual(errors,[]);console.log('PASS reset conflict requires refresh, access denial clears private state, untagged mission never reads rankings');
}finally{if(browser)await browser.close();await server.close();}
