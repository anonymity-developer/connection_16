import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom',logLevel:'error'});
after(()=>server.close());
const {createCollectionCelebrations}=await server.ssrLoadModule('/src/features/participation/collection-celebrations.ts');

test('a game reset preserves first S but allows new letters and completion celebrations once in each round',()=>{
  const original=globalThis.localStorage,storage=new Map();
  globalThis.localStorage={getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,value)};
  try {
    const firstS=['SHOW_ME'],all=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'];
    const board=createCollectionCelebrations(42);board.observe(firstS);
    assert.deepEqual(board.takeLetters(all),all.slice(1));assert.equal(board.takeCompletion(),true);
    const reloaded=createCollectionCelebrations(42);reloaded.observe(all);
    assert.deepEqual(reloaded.takeLetters(all),[]);assert.equal(reloaded.takeCompletion(),false);
    reloaded.setGeneration(1);reloaded.observe(firstS);
    assert.deepEqual(reloaded.takeLetters(firstS),[]);assert.deepEqual(reloaded.takeLetters(all),all.slice(1));
    assert.equal(reloaded.takeCompletion(),true);assert.equal(reloaded.takeCompletion(),false);
    const nextVisit=createCollectionCelebrations(42);nextVisit.setGeneration(1);nextVisit.observe(all);
    assert.deepEqual(nextVisit.takeLetters(all),[]);assert.equal(nextVisit.takeCompletion(),false);
    const anotherUser=createCollectionCelebrations(43);anotherUser.setGeneration(1);anotherUser.observe(firstS);
    assert.deepEqual(anotherUser.takeLetters(all),all.slice(1));assert.equal(anotherUser.takeCompletion(),true);
  }finally{if(original===undefined)delete globalThis.localStorage;else globalThis.localStorage=original;}
});
