/* global document, window, getComputedStyle */
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { preview } from 'vite';
const output=resolve('../output/playwright/cheer-mobile');await mkdir(output,{recursive:true});
const server=await preview({preview:{host:'127.0.0.1',port:4282,strictPort:true}});let browser;
try {
 browser=await chromium.launch();const page=await browser.newPage({viewport:{width:390,height:840},hasTouch:true});
 const errors=[],codes=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'];page.on('pageerror',e=>errors.push(e.message));
 const cards=Array.from({length:25},(_,i)=>({id:i+1,content:`응원 목록 검사 ${i+1}`,templateId:'NOTE',style:['ROSE','INK','MINT'][i%3],anonymous:i%2===0,nickname:i%2===0?null:`동료 ${i+1}`,profileId:i%2===0?null:i+100,luckScore:null,landingZone:null}));
 const hearts=Object.fromEntries(cards.map(c=>[c.id,{liked:false,count:c.id%4,canLike:true}]));
 let feedReads=0,puts=0,releaseWrite=null,failWrite=false;
 await page.route('**/api/v1/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname.slice(7),reply=(json,status=200)=>route.fulfill({status,json});
  try {
   if(path==='/missions/show-me/reconcile')return reply({newlyCompleted:false});
   if(path==='/auth/config')return reply({available:true,passwordMinLength:8,passwordMaxLength:20});
   if(path==='/users/me')return reply({id:73,username:'cheer-mobile-fixture',nickname:'내 응원 닉네임',onboardingRequired:false});
   if(path==='/tags/pending')return reply({present:false});
   if(path==='/csrf')return reply({headerName:'X-XSRF-TOKEN',token:'cheer-mobile-csrf'});
   if(path==='/event')return reply({status:'OPEN',opensAt:null,submitClosesAt:null,profilePreparationEnabled:false});
   if(path==='/missions/collection')return reply({missions:codes.map((code,i)=>({id:i+1,code,active:true,state:'UNLOCKED'})),completedCount:0,completion:{complete:false,completionOrder:null,completedAt:null,awardConfirmed:false},resetGeneration:0});
   if(path==='/cheers/me')return reply({id:null,version:0,visibility:null,approvedRevision:null,pendingRevision:null,latestRevision:null,missionCompleted:false,luckScore:null,landingZone:null});
   if(path==='/cheers'){
    feedReads++;const sort=url.searchParams.get('sort'),direction=url.searchParams.get('direction'),ascending=direction==='ASC';assert.ok(['LATEST','HEARTS'].includes(sort));
    const sorted=[...cards].sort((a,b)=>(ascending?1:-1)*((sort==='HEARTS'?hearts[a.id].count-hearts[b.id].count:0)||a.id-b.id));
    const offset=Number(url.searchParams.get('cursor')||0);return reply({items:sorted.slice(offset,offset+20),nextCursor:offset+20<sorted.length?offset+20:null});
   }
   if(path==='/cheers/likes')return reply(Object.fromEntries(url.searchParams.get('ids').split(',').map(id=>[id,hearts[id]])));
   if(/^\/cheers\/\d+\/like$/.test(path)){
    const id=Number(path.split('/')[2]);if(req.method()==='GET')return reply(hearts[id]);
    assert.equal(req.headers()['x-xsrf-token'],'cheer-mobile-csrf');assert.equal(req.headers()['x-participation-generation'],'0');
    puts++;await new Promise(resolve=>{releaseWrite=resolve;});releaseWrite=null;
    if(failWrite){failWrite=false;return reply({message:'변경 실패'},503);}
    const desired=req.method()==='PUT';hearts[id]={...hearts[id],liked:desired,count:hearts[id].count+(desired?1:-1)};return desired?reply(hearts[id]):route.fulfill({status:204});
   }
   throw new Error(`Unexpected ${req.method()} ${path}`);
  }catch(e){errors.push(e.message);return reply({message:'검사 실패'},500);}
 });
 await page.goto('http://127.0.0.1:4282/missions/say-something');try { await page.locator('.content-feed-list li').nth(19).waitFor({timeout:8000}); } catch(e) { console.log('Fixture diagnostics',errors,await page.locator('body').innerText());throw e; }
 assert.equal(feedReads,1);assert.equal(await page.locator('.content-feed-list li').count(),20);assert.equal(await page.getByRole('radio',{name:'작은 편지',exact:true}).count(),0);
 const anonymous=page.getByRole('checkbox',{name:'익명으로 남기기',exact:true}),preview=page.getByLabel('응원 카드 미리보기',{exact:true});
 assert.equal(await anonymous.isChecked(),true);assert.equal(await anonymous.evaluate(el=>el.getBoundingClientRect().width),18);
 await anonymous.uncheck();assert.equal(await preview.locator('.content-byline').innerText(),'내 응원 닉네임');await anonymous.check();assert.equal(await preview.locator('.content-byline').innerText(),'익명');
 const colours=page.locator('.cheer-colour-options input');assert.equal(await colours.count(),10);
 const backgrounds=new Set();
 for(let i=0;i<10;i++){await colours.nth(i).check();backgrounds.add(await preview.evaluate(el=>getComputedStyle(el).backgroundColor));assert.equal(await colours.nth(i).isChecked(),true);}
 assert.equal(backgrounds.size,10);
 for(const width of [360,390,430,768,1440]){
  await page.setViewportSize({width,height:840});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);
  for(const chip of await page.locator('.cheer-colour-chip').all()){const bounds=await chip.boundingBox();assert.ok(bounds.width>=44&&bounds.height>=44);}
  await page.screenshot({path:resolve(output,`write-${width}.png`),fullPage:true});
  await page.locator('.content-own').screenshot({path:resolve(output,`composer-${width}.png`)});
 }
 await page.setViewportSize({width:390,height:840});await page.getByRole('button',{name:'응원 더 보기',exact:true}).click();await page.locator('.content-feed-list li').nth(24).waitFor();assert.equal(feedReads,2);assert.equal(await page.getByRole('button',{name:'응원 더 보기',exact:true}).count(),0);
 assert.equal(await page.locator('.content-feed-list li').first().locator('.content-body').innerText(),'응원 목록 검사 25');
 await page.getByRole('button',{name:'최신순 오름차순으로 전환',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.content-feed-list li .content-body')?.textContent==='응원 목록 검사 1');
 await page.getByRole('button',{name:'하트순',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.content-feed-list li .content-body')?.textContent==='응원 목록 검사 23');
 const first=page.locator('.content-feed-list li').first(),control=first.locator('.heart-control');
 const before=await page.locator('.content-feed-list .content-body').allInnerTexts();
 await control.getByRole('button',{name:'하트 남기기',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.heart-control')?.getAttribute('aria-busy')==='true');
 assert.equal(await control.locator('.heart-count').innerText(),'4');assert.equal(await control.locator('svg').evaluate(el=>getComputedStyle(el).fill),await control.locator('svg').evaluate(el=>getComputedStyle(el).color));assert.equal(await control.getByRole('button',{name:'하트 취소하기',exact:true}).getAttribute('aria-pressed'),'true');
 assert.deepEqual(await page.locator('.content-feed-list .content-body').allInnerTexts(),before);assert.equal(puts,1);assert.equal(await page.getByRole('button',{name:'목록 새로고침',exact:true}).isDisabled(),true);while(!releaseWrite)await page.waitForTimeout(10);releaseWrite();await page.waitForFunction(()=>document.querySelector('.heart-control')?.getAttribute('aria-busy')==='false');
 failWrite=true;await control.getByRole('button',{name:'하트 취소하기',exact:true}).click();assert.equal(await control.locator('.heart-count').innerText(),'3');while(!releaseWrite)await page.waitForTimeout(10);releaseWrite();await control.getByRole('alert').waitFor();assert.equal(await control.locator('.heart-count').innerText(),'4');assert.equal(puts,2);
 await page.getByRole('button',{name:'하트순 오름차순으로 전환',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.content-feed-list li .content-body')?.textContent==='응원 목록 검사 4');
 for(const row of await page.locator('.content-feed-list li').all()){const author=await row.locator('.content-byline').innerText();if(author==='익명')assert.equal(await row.locator('.content-byline a').count(),0);}
 assert.deepEqual(errors,[]);console.log('PASS ten visible colour chips; real checkbox/nickname/anonymous; five widths; 20+5 pagination; four sort directions; optimistic heart before server response; failed cancel restores server truth; cards stay put; anonymous identities have no links');
}finally{if(browser)await browser.close();await server.close();}
