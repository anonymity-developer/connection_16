import { useEffect, useRef, useState } from 'react';
import type { CSSProperties, PointerEvent } from 'react';
import { Button } from '../components/ui';
import { WishGallery } from '../features/content/WishGallery';
import { WishComments } from '../features/content/WishComments';
import type { Experience } from './experience-store';
export type Landing = 'statue' | 'water';
export function chooseLanding(x:number,y:number):Landing { return x>=.34&&x<=.66&&y>=.18&&y<=.5?'statue':'water'; }
export function FountainExperience({store}:{store:Experience}) {
  const [text,setText]=useState(store.slots.wishes.latestRevision?.content||'');const [phase,setPhase]=useState<'write'|'issued'|'flying'|'landed'>(store.slots.wishes.id?'landed':'write');const [landing,setLanding]=useState<Landing>(store.wishLanding||'water');const [error,setError]=useState('');const [busy,setBusy]=useState(false);
  const [dragging,setDragging]=useState(false);const [point,setPoint]=useState({x:50,y:90});const input=useRef<HTMLTextAreaElement>(null);const scene=useRef<HTMLDivElement>(null);const key=useRef<string|null>(null);const posting=useRef(false);const active=useRef(true);const pointer=useRef<number|null>(null);
  const [anonymous,setAnonymous]=useState(store.slots.wishes.latestRevision?.anonymous ?? true);
  useEffect(()=>{active.current=true;return()=>{active.current=false;};},[]);
  useEffect(()=>{if(phase!=='flying')return;const timer=window.setTimeout(()=>setPhase('landed'),1250);return()=>clearTimeout(timer);},[phase]);
  function issue(){if(!text.trim()){setError('동전에 담을 소원을 적어주세요.');input.current?.focus();return;}setError('');key.current=crypto.randomUUID();setPhase('issued');}
  async function toss(where:Landing){if(posting.current||phase!=='issued')return;posting.current=true;setBusy(true);setError('');setDragging(false);
    try{const slot=store.slots.wishes;await store.content.submit('wishes',!!slot.id,key.current!,{content:text.trim(),templateId:'PLAIN',style:'ROSE',anonymous,expectedVersion:slot.id?slot.version:null});if(active.current){store.rememberLanding(where);setLanding(where);setPhase('flying');}}
    catch{if(active.current)setError('가상 접수에 실패했어요. 같은 동전으로 다시 던져주세요.');}
    finally{posting.current=false;if(active.current)setBusy(false);}
  }
  function move(event:PointerEvent){if(pointer.current!==event.pointerId||!scene.current)return;const r=scene.current.getBoundingClientRect();setPoint({x:Math.min(95,Math.max(5,(event.clientX-r.left)/r.width*100)),y:Math.min(95,Math.max(5,(event.clientY-r.top)/r.height*100))});}
  function release(event:PointerEvent){if(pointer.current!==event.pointerId||!scene.current)return;const r=scene.current.getBoundingClientRect();pointer.current=null;void toss(chooseLanding((event.clientX-r.left)/r.width,(event.clientY-r.top)/r.height));}
  const slot=store.slots.wishes;
  const privateStatue=store.wishLanding==='statue'&&!!slot.id;
  const overlay=<>
      {phase==='issued'&&<button type="button" className={`throw-coin ${dragging?'is-dragging':''}`} aria-label="소원 동전 던지기" disabled={busy} style={dragging?{left:point.x+'%',top:point.y+'%'}:undefined} onPointerDown={e=>{e.preventDefault();pointer.current=e.pointerId;e.currentTarget.setPointerCapture(e.pointerId);setDragging(true);move(e);}} onPointerMove={move} onPointerUp={release} onPointerCancel={()=>{pointer.current=null;setDragging(false);}} onClick={e=>{if(e.detail===0)void toss('water');}}>A<span>던져봐요</span></button>}
      {phase==='flying'&&<div className={`throw-flight landing-${landing}`} style={{'--landing-y':landing==='statue'?'37.5%':'69%'} as CSSProperties} aria-hidden="true"><span>A</span></div>}
      {phase==='landed'&&privateStatue&&<span className="private-landing" aria-label="분수 위에 안착한 내 동전">A</span>}
      {phase==='flying'&&landing==='statue'&&<span className="fountain-twinkle" aria-hidden="true"><svg viewBox="0 0 24 24" width="24" height="24"><path d="m12 0 3 9 9 3-9 3-3 9-3-9-9-3 9-3Z" fill="currentColor" /></svg></span>}
      {phase==='flying'&&landing==='water'&&<span className="fountain-splash" aria-hidden="true"/>}
  </>;
  return <section className="demo-fountain">
    <form className="demo-wish-composer" onSubmit={e=>{e.preventDefault();issue();}}><label htmlFor="demo-wish">분수에 전하고 싶은 소원</label><textarea ref={input} id="demo-wish" maxLength={400} rows={3} value={text} disabled={phase!=='write'} onChange={e=>setText(e.target.value)}/><small>{text.length}/400 · 가상 소원, 실제 전송 없음</small><label className="content-choice"><input type="checkbox" checked={anonymous} disabled={phase!=='write'} onChange={event=>setAnonymous(event.target.checked)} />익명으로 남기기</label>{phase==='write'&&<Button type="submit">소원 동전 받기</Button>}</form>
    {phase==='issued'&&<p role="status" className="demo-coin-notice">소원 동전이 발급됐어요! 소원 동전을 동상에 던져보세요!</p>}
    <div className={`demo-throw-scene phase-${phase}`}>
      <WishGallery sceneRef={scene} sceneOverlay={overlay} items={store.feeds.wishes} renderComments={id=><WishComments id={id} api={store.reactions}/>}/>

    </div>
    {phase==='issued'&&<div className="demo-actions"><Button disabled={busy} onClick={()=>void toss('statue')}>동상 쪽으로 던지기</Button><Button disabled={busy} variant="secondary" onClick={()=>void toss('water')}>물 쪽으로 던지기</Button><Button disabled={busy} variant="quiet" onClick={()=>{setPhase('write');key.current=null;}}>소원 고치기</Button></div>}
    {(phase==='flying'||phase==='landed')&&<p className="demo-coin-notice" role="status">{store.wishLanding ? landing==='statue'?'동상에 살포시 안착했어요!':'퐁당! 물결에 소원을 실었어요.' : '가상 소원 상태를 확인해요.'} </p>}
    {phase==='landed'&&<Button variant="quiet" onClick={()=>{setPhase('write');key.current=null;}}>같은 가상 소원 수정하기</Button>}
    {error&&<p role="alert" className="form-notice is-error">{error}</p>}
  </section>;
}
