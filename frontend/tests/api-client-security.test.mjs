import test from 'node:test';
import assert from 'node:assert/strict';
import { request, authApi, ApiError, clearCsrf } from '../src/features/auth/api.ts';

// Explicit in-process fetch doubles only: this file never contacts an API or creates an account.
function withFetch(handler, run) {
  const original = globalThis.fetch;
  globalThis.fetch = handler;
  clearCsrf();
  return Promise.resolve().then(run).finally(() => { globalThis.fetch = original; clearCsrf(); });
}
const json = (body, status=200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
test('game writes carry the loaded reset generation, keep it after 403 and discard another session’s delayed collection', async () => {
  let delayed, hold=false, denied=false;const generations=[];
  await withFetch(async(url,options)=>{
    if(url.endsWith('/csrf'))return json({headerName:'X-XSRF-TOKEN',token:'fixture'});
    if(url.endsWith('/missions/collection'))return hold?new Promise(resolve=>{delayed=resolve;}):json({resetGeneration:3});
    if(url.endsWith('/tap/start')){generations.push(options.headers['X-Participation-Generation']);return json({attemptId:1});}
    if(denied)return json({code:'SELF_LIKE',message:'test-only rejection'},403);
    return json({id:1});
  },async()=>{
    await authApi.login('fixture','fixture-password-only');await request('/missions/collection');
    await request('/tap/start','POST');denied=true;
    await assert.rejects(request('/cheers/1/like','PUT'));denied=false;
    await request('/tap/start','POST');
    hold=true;const old=request('/missions/collection');
    await authApi.login('another-fixture','fixture-password-only');delayed(json({resetGeneration:9}));await old;
    await request('/tap/start','POST');assert.deepEqual(generations,['3','3','0']);
  });
});
test('writes require CSRF and never send a service password in the URL or CSRF request', async () => {
  const calls=[];
  await withFetch(async(url, options) => {
    calls.push({url,options});
    return url.endsWith('/csrf') ? json({ headerName: 'X-XSRF-TOKEN', token: 'fixture-csrf' }) : json({id:1});
  }, async () => {
    await authApi.login('fixture', 'fixture-password-only');
    assert.deepEqual(calls.map(c=>c.url), ['/api/v1/csrf','/api/v1/auth/login']);
    assert.equal(calls[0].options.body, undefined);
    assert.equal(calls[1].options.headers['X-XSRF-TOKEN'], 'fixture-csrf');
    assert.ok(calls.every(c=>c.options.credentials==='same-origin' && !c.url.includes('fixture-password-only')));
  });
});
test('successful session rotation invalidates the cached CSRF proof before the next write', async () => {
  let csrfReads=0; const writeTokens=[];
  await withFetch(async(url, options) => {
    if(url.endsWith('/csrf')) return json({headerName:'X-XSRF-TOKEN', token:'fixture-'+(++csrfReads)});
    writeTokens.push(options.headers['X-XSRF-TOKEN']); return json({id:1});
  },async()=>{await authApi.login('fixture','fixture-password-only'); await request('/tags/pending','POST',{token:'synthetic-not-a-real-tag'});assert.deepEqual(writeTokens,['fixture-1','fixture-2']);});
});
test('an expired session announces reauthentication but ordinary me probing does not', async () => {
  const original=globalThis.window; const events=new EventTarget(); let expired=0;
  events.addEventListener('tagme:unauthenticated',()=>expired++);globalThis.window=events;
  try { await withFetch(async()=>json({code:'SESSION_EXPIRED',message:'fixture'},401),async()=>{
    await assert.rejects(request('/missions/collection'),error=>error instanceof ApiError&&error.status===401);
    assert.equal(expired,1);await assert.rejects(authApi.me(),ApiError);assert.equal(expired,1);
  }); } finally { if(original===undefined)delete globalThis.window;else globalThis.window=original; }
});
test('network failures never silently retry mutation requests', async () => {
  let writes=0;
  await withFetch(async(url)=>{
    if(url.endsWith('/csrf'))return json({headerName:'X-XSRF-TOKEN',token:'fixture'});
    writes++;throw new Error('simulated transport failure');
  },async()=>{await assert.rejects(request('/wishes','POST',{content:'fixture'}),error=>error instanceof ApiError&&error.status===503&&error.code==='NETWORK_ERROR');assert.equal(writes,1);});
});
