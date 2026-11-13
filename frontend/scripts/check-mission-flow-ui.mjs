/* global document, innerWidth, getComputedStyle, history, dispatchEvent */
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';

// Synthetic browser/API contracts only: no real tags, accounts or game records.
const output = resolve('../output/playwright'); await mkdir(output, { recursive: true });
const server = await preview({ preview: { host: '127.0.0.1', port: 4277, strictPort: true } });
let browser;
try {
  browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  const codes = ['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'];
  const names = ['SHOW ME','SYNC ME','A WISH COME TRUE','FAST TAP','YOU GOT THIS'];
  const tokens = codes.map((_,i) => String.fromCharCode(65+i).repeat(43));
  const errors = [], writes = [], completed = new Set(['SHOW_ME']), unlocked = new Set();
  let signedIn = false, generation = 0, resolves = 0, serial = 100, heldInspection = null;
  const blank = () => ({id:null,version:0,visibility:null,approvedRevision:null,pendingRevision:null,latestRevision:null,missionCompleted:false,luckScore:null,landingZone:null});
  const slots = { wishes:blank(), cheers:blank() }; let sync = null;
  page.on('pageerror',e => errors.push(e.message));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.slice(7), method = request.method();
    const reply = (status, json) => route.fulfill({ status, json });
    try {
      if(method === 'GET') {
        if(path === '/auth/config')return reply(200,{available:true,passwordMinLength:8,passwordMaxLength:20});
        if(path === '/users/me')return signedIn ? reply(200,{id:73,username:'flow-contract',nickname:'검사용 참가자',onboardingRequired:false}) : reply(401,{code:'UNAUTHENTICATED'});
        if(path === '/tags/pending')return reply(200,{present:false});
        if(path === '/csrf')return reply(200,{headerName:'X-XSRF-TOKEN',token:'flow-csrf'});
        if(path === '/event')return reply(200,{status:'OPEN',opensAt:null,submitClosesAt:null,profilePreparationEnabled:false});
        if(path === '/missions/collection')return reply(200,{missions:codes.map((code,i)=>({id:i+1,code,active:true,state:completed.has(code)?'COMPLETED':unlocked.has(code)?'UNLOCKED':'LOCKED'})),completedCount:completed.size,completion:{complete:false},resetGeneration:generation});
        for(const kind of ['wishes','cheers']) {
          if(path === `/${kind}/me`)return reply(200,slots[kind]);
          if(path === `/${kind}`)return reply(200,{items:[],nextCursor:null});
        }
        if(path === '/sync/me')return reply(200,{result:sync});
        if(path === '/sync/questions')return reply(200,{available:true,version:'fixture-v1',title:'검사용 질문',questions:[{id:'q1',text:'검사용 취향 선택',options:[{id:'a',label:'함께 만들기'}]}]});
        if(path === '/discovery/people')return reply(200,{items:[],nextCursor:null});
        if(path === '/tap/me')return reply(200,{officialAvailable:false,attempt:{attemptId:9,status:'INVALID',tapCount:2,reviewReason:'DUPLICATE_MILLISECOND'}});
        if(path === '/tap/leaderboard')return reply(200,{top:[],own:null});
      }
      assert.equal(request.headers()['x-xsrf-token'],'flow-csrf');
      const body = request.postDataJSON();
      if(path === '/tags/inspect') {
        const i=tokens.indexOf(body.token);
        if(i===2 && heldInspection)await heldInspection;
        return i<0 ? reply(404,{code:'TAG_NOT_FOUND',message:'태그를 확인할 수 없어요.'}) : reply(200,{missionCode:codes[i],letter:'SSAFY'[i],title:names[i]});
      }
      writes.push(path);
      if(path === '/tags/resolve') {
        const i=tokens.indexOf(body.token); assert.ok(signedIn && i>=0); resolves++; unlocked.add(codes[i]);
        return reply(200,{missionCode:codes[i],state:completed.has(codes[i])?'COMPLETED':'UNLOCKED',route:`/missions/${codes[i].toLowerCase().replaceAll('_','-')}`,newlyUnlocked:true,newlyCompleted:false});
      }
      for(const [kind,code] of [['wishes','MAKE_A_WISH'],['cheers','SAY_SOMETHING']])if(path === `/${kind}` || path === `/${kind}/me`) {
        assert.ok(unlocked.has(code)); assert.equal(request.headers()['x-participation-generation'],String(generation));
        const revision={...body,id:++serial,itemId:serial,number:1,status:'APPROVED',reason:null,submittedAt:'2026-01-01T00:00:00Z'};
        slots[kind]={...blank(),id:serial,version:slots[kind].version+1,visibility:'VISIBLE',approvedRevision:revision,latestRevision:revision,missionCompleted:true}; completed.add(code);return reply(202,slots[kind]);
      }
      if(path === '/sync/submit') {
        assert.ok(unlocked.has('SYNC_ME')); assert.equal(body.answers.length,1);
        sync={questionnaireVersion:'fixture-v1',type:{code:'T',title:'검사용 성향',description:'브라우저 검사 전용 결과'},axisScores:{},submittedAt:'2026-01-01T00:00:00Z'};completed.add('SYNC_ME');return reply(200,sync);
      }
      throw new Error(`Unexpected contract request: ${method} ${path}`);
    }catch(error){errors.push(error.message);return reply(500,{message:'계약 검사 실패'});}
  });
  const visit=path=>page.goto(`http://127.0.0.1:4277${path}`);
  for(const width of [360,390,430,768,1440]) {
    await page.setViewportSize({width,height:900});
    for(let i=0;i<codes.length;i++) {
      await visit(`/t/${tokens[i]}`);
      const letter=i===0?'소개 S':i===1?'취향 S':'SSAFY'[i];
      await page.getByRole('heading',{name:`${letter} 미션을 찾았어요`,exact:true}).waitFor();
      await page.getByRole('button',{name:'로그인하고 이어가기',exact:true}).waitFor();
      assert.equal(await page.locator('body').getByText(/QR/).count(),0);
      assert.equal(await page.locator('.tag-discovery .is-collected').count(),0);
      assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
      if(i===2 && [390,1440].includes(width)) {
        await page.locator('.tag-discovery-artwork').evaluate(el=>Promise.all(el.getAnimations().map(animation=>animation.finished)));
        await page.screenshot({path:resolve(output,`nfc-found-a-${width}.png`),fullPage:true});
      }
    }
  }
  assert.equal(resolves,0);assert.deepEqual(writes,[]);
  console.log('PASS five NFC identities before login at five widths; metadata never unlocks or awards');
  let releaseInspection;
  heldInspection=new Promise(done=>{releaseInspection=done;});
  const oldRequest=page.waitForRequest(request=>request.url().endsWith('/tags/inspect') && request.postDataJSON().token===tokens[2]);
  await visit(`/t/${tokens[2]}`);await oldRequest;
  await page.evaluate(path=>{history.pushState(null,'',path);dispatchEvent(new Event('tagme:navigation'));},`/t/${tokens[3]}`);
  await page.getByRole('heading',{name:'F 미션을 찾았어요',exact:true}).waitFor();
  const oldResponse=page.waitForResponse(response=>response.url().endsWith('/tags/inspect') && response.request().postDataJSON().token===tokens[2]);
  releaseInspection();heldInspection=null;await oldResponse;await page.waitForLoadState('networkidle');
  assert.equal(await page.getByRole('heading',{name:'F 미션을 찾았어요',exact:true}).count(),1);
  console.log('PASS a delayed previous tag response cannot replace the current NFC identity');
  signedIn=true;await page.setViewportSize({width:390,height:900});
  await visit(`/t/${tokens[2]}`);await page.getByRole('button',{name:'소원 남기기',exact:true}).click();
  await page.waitForURL('**/missions/make-a-wish');await page.locator('#content-text').waitFor();
  assert.equal(await page.locator('.mission-detail-heading .context-label').count(),0);
  assert.equal(await page.getByRole('heading',{name:names[2],exact:true}).count(),1);
  assert.equal(await page.getByRole('dialog').count(),0);
  await page.locator('#content-text').fill('브라우저 검사 전용 소원');await page.getByRole('button',{name:'동전 던지기',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'A를 획득했어요',exact:true});await dialog.waitFor();
  assert.equal(new URL(page.url()).pathname,'/missions/make-a-wish');
  for(const width of [360,390,430,768,1440]) {
    await page.setViewportSize({width,height:900});assert.ok(await dialog.evaluate(el=>el.scrollWidth<=el.clientWidth));
    if([390,1440].includes(width))await page.screenshot({path:resolve(output,`mission-earned-a-${width}.png`)});
  }
  await page.emulateMedia({reducedMotion:'reduce'});
  assert.equal(await dialog.locator('.letter-sticker').evaluate(el=>getComputedStyle(el).animationName),'none');
  await page.keyboard.press('Tab');assert.ok(await page.evaluate(()=>!!document.activeElement.closest('dialog')));
  await page.keyboard.press('Escape');assert.equal(await page.getByRole('dialog').count(),0);
  await page.reload();await page.getByRole('button',{name:'같은 글 수정하기',exact:true}).waitFor();assert.equal(await page.getByRole('dialog').count(),0);
  await page.getByRole('button',{name:'같은 글 수정하기',exact:true}).click();await page.locator('#content-text').fill('같은 소원 수정 검사');await page.getByRole('button',{name:'수정 내용 보내기',exact:true}).click();await page.getByRole('button',{name:'같은 글 수정하기',exact:true}).waitFor();assert.equal(await page.getByRole('dialog').count(),0);
  console.log('PASS real A completion opens accessible reward, stays on game, respects reduced motion and never repeats on reload/edit');
  for(const [i,action] of [[1,'취향 알아보기'],[4,'응원 남기기']]) {
    await visit(`/t/${tokens[i]}`);await page.getByRole('button',{name:action,exact:true}).click();
    if(i===1){await page.getByLabel('함께 만들기',{exact:true}).check();await page.getByRole('button',{name:'결과 확인하기',exact:true}).click();}
    else{await page.locator('#content-text').fill('브라우저 검사 전용 응원');await page.getByRole('button',{name:'응원 보내기',exact:true}).click();}
    dialog=page.getByRole('dialog',{name:i===1?'두 번째 S를 획득했어요':'Y를 획득했어요',exact:true});await dialog.waitFor();
    await dialog.getByRole('button',{name:'계속 참여하기',exact:true}).click();assert.ok(new URL(page.url()).pathname.startsWith('/missions/'));
  }
  await visit(`/t/${tokens[3]}`);await page.getByRole('button',{name:'탭 도전하기',exact:true}).click();await page.getByRole('heading',{name:'기록이 자동 무효 처리됐어요',exact:true}).waitFor();assert.equal(await page.getByRole('dialog').count(),0);
  console.log('PASS SYNC/Y reward uses server completion and invalid TAP never earns F');
  generation++;completed.delete('MAKE_A_WISH');slots.wishes=blank();
  await visit('/missions/make-a-wish');await page.locator('#content-text').fill('초기화 후 새 회차 검사');await page.getByRole('button',{name:'동전 던지기',exact:true}).click();
  dialog=page.getByRole('dialog',{name:'A를 획득했어요',exact:true});await dialog.waitFor();await dialog.getByRole('link',{name:'수집판 보기',exact:true}).click();await page.waitForURL('http://127.0.0.1:4277/');assert.equal(await page.getByRole('dialog').count(),0);
  await visit('/t/'+ 'Z'.repeat(43));await page.getByRole('alert').waitFor();assert.equal(await page.locator('.tag-discovery-artwork').count(),0);
  assert.deepEqual(errors,[]);console.log('PASS reset generation can celebrate again, collection navigation is optional and invalid NFC never shows a discovery');
}finally{await browser?.close();await new Promise(done=>server.httpServer.close(done));}
