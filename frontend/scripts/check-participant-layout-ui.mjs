/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {chromium} from 'playwright';
import {preview} from 'vite';
const output=resolve('../output/playwright');await mkdir(output,{recursive:true});
const server=await preview({preview:{host:'127.0.0.1',port:4584,strictPort:true}});let browser;
try {
 browser=await chromium.launch();const page=await browser.newPage({viewport:{width:390,height:900}});
 let account=null,completed=0;const codes=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'],errors=[],unexpected=[],writes=[],checks=[];
 page.on('pageerror',error=>errors.push(error.message));
 await page.route('**/api/v1/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname.slice(7);if(req.method()!=='GET')writes.push(path);
  const json=path==='/users/me'?account===null?null:{id:account,username:'layout-fixture',nickname:'화면 검사',onboardingRequired:false}
    :path==='/auth/config'?{available:true,passwordMinLength:8,passwordMaxLength:20}
    :path==='/tags/pending'?{present:false}:path==='/event'?{status:'CLOSED'}
    :path==='/missions/collection'?{missions:codes.map((code,i)=>({code,state:i<completed?'COMPLETED':'LOCKED',active:true})),completedCount:completed,resetGeneration:0,completion:{complete:completed===5,completionOrder:completed===5?7:null,awardConfirmed:false}}
    :path==='/discovery/recommendations'?[]:path==='/discovery/stats'?{visitedCount:0,visitorCount:0,mutualCount:0}:path==='/interests'?[]:undefined;
  if(path==='/users/me'&&json===null)return route.fulfill({status:401,json:{code:'UNAUTHENTICATED',message:'로그인이 필요해요.'}});
  if(json===undefined){unexpected.push(`${req.method()} ${path}`);return route.fulfill({status:500,json:{message:'Unexpected layout fixture request'}});}
  return route.fulfill({json});
 });
 const visit=async path=>{await page.goto(`http://127.0.0.1:4584${path}`);await page.evaluate(()=>document.fonts.ready);};
 const record=text=>{checks.push(text);console.log('PASS '+text);};
 const metrics=selector=>page.locator(selector).evaluateAll(elements=>elements.map(el=>{const r=el.getBoundingClientRect(),s=getComputedStyle(el);return {x:r.x,y:r.y,width:r.width,height:r.height,font:s.fontFamily,size:s.fontSize,weight:s.fontWeight,line:s.lineHeight,padding:s.padding,border:s.border,radius:s.borderRadius,shadow:s.boxShadow,background:s.backgroundColor};}));
 const widths=[360,390,430,640,641,768,900,901,1099,1100,1440];
 for(const width of widths){
  await page.setViewportSize({width,height:900});account=null;
  await visit('/discovery');await page.getByRole('heading',{name:'함께할 동료를 만나봐요',exact:true}).waitFor();
  const guestHeading=await metrics('.page-heading > *'),guestCard=await metrics('.participant-signin-card,.participant-signin-card .empty-symbol,.participant-signin-card h2,.participant-signin-card > p,.participant-signin-card .button');
  await visit('/me');await page.getByRole('heading',{name:'나의 연결 기록을 만나봐요',exact:true}).waitFor();
  assert.deepEqual(await metrics('.page-heading > *'),guestHeading,`guest headers match at ${width}`);
  assert.deepEqual(await metrics('.participant-signin-card,.participant-signin-card .empty-symbol,.participant-signin-card h2,.participant-signin-card > p,.participant-signin-card .button'),guestCard,`guest cards share geometry and type at ${width}`);
  if([390,1440].includes(width))await page.screenshot({path:resolve(output,`participant-account-guest-${width}.png`),fullPage:true});
  let baseline;
  for(const state of ['guest','collecting','complete']){
   account=state==='guest'?null:width*10+(state==='complete'?1:0);completed=state==='collecting'?2:state==='complete'?5:0;
   await visit('/');await page.getByRole('heading',{name:'나의 SSAFY 수집판',exact:true}).waitFor();
   if(account!==null)await page.waitForFunction(count=>document.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')===String(count),completed);
   else assert.equal(await page.locator('.collection-count,[role="progressbar"]').count(),0,'guest never gets an invented count');
   assert.equal(await page.locator('.sticker-board h2').count(),0,'collection heading is outside the board');
   assert.equal(await page.locator('.sticker-board .collection-count').count(),0,'earned count stays beside the external title');
   if(account!==null){const heading=await page.locator('.collection-section > .section-heading').boundingBox(),count=await page.locator('.collection-section > .section-heading .collection-count').boundingBox();assert.ok(Math.abs(count.x+count.width-heading.x-heading.width)<1);}
   if(state!=='complete'){const board=await page.locator('.sticker-board').boundingBox(),bubble=await page.locator('.collection-bubble').boundingBox();assert.ok(Math.abs(bubble.x+bubble.width/2-board.x-board.width/2)<1,'invitation is centered for every collecting state');}
   for(const row of await page.locator('.mission-section .mission-row').all()){const art=await row.locator('.letter-sticker').boundingBox(),copy=await row.locator('.mission-row-copy').boundingBox();assert.ok(copy.x-art.x-art.width>=(width<=640?18:22),`artwork has a real gap before text at ${width}`);}
   const headings=await metrics('.home-page .participant-section-heading h2');assert.equal(headings.length,2);assert.equal(headings[0].size,headings[1].size);assert.equal(headings[0].weight,headings[1].weight);assert.equal(headings[0].x,headings[1].x);
   const layout=await metrics('.collection-section > .section-heading,.sticker-board,.letter-slots,.collection-progress-slot,.collection-footer,.home-page > .event-encouragement,.mission-section');
   if(baseline)assert.deepEqual(layout,baseline,`${state} retains collection and section positions at ${width}`);else baseline=layout;
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
   if([360,390,430,768,1440].includes(width))await page.screenshot({path:resolve(output,`participant-home-${state}-${width}.png`),fullPage:true});
  }
  account=width*10+2;completed=2;await visit('/discovery');await page.getByRole('heading',{name:'대화를 시작할 동료',exact:true}).waitFor();
  const memberHeading=await metrics('.page-heading > *');await visit('/me');await page.getByRole('heading',{name:'계정 연결을 마쳤어요',exact:true}).waitFor();assert.deepEqual(await metrics('.page-heading > *'),memberHeading,`signed-in headings match at ${width}`);
 }
 record('one participant page and sign-in card share width, shape, type and spacing across guest/member states at eleven widths');
 record('collection heading matches the mission section outside the board; guest/collecting/complete positions stay stable without fake guest counts');
 completed=5;account=999;await visit('/complete');await page.getByRole('heading',{name:'다섯 글자를 모두 모았어요',exact:true}).waitFor();await page.getByText('운영상 완료 순번 7번으로 기록됐어요.',{exact:true}).waitFor();assert.equal(await page.getByText('수상 순위는 아직 확정되지 않았어요.',{exact:false}).count(),0);
 record('completion record retains the actual completion order and removes the unconfirmed-award sentence');
 assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);assert.deepEqual(writes,[]);await writeFile(resolve(output,'participant-layout-checks.json'),JSON.stringify({kind:'production build with explicit API fixtures; no live participation',checks,errors,unexpected,writes},null,2));
}finally{await browser?.close();await server.httpServer.close();}
