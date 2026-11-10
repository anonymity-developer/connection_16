import { useState, useSyncExternalStore } from 'react';
import { createRoot } from 'react-dom/client';
import { Button, Field } from '../components/ui';
import { LetterSticker } from '../components/LetterSticker';
import { Collection, MissionCards, EventEncouragement } from '../pages/Home';
import { missions } from '../features/missions/model';
import type { MissionState } from '../features/missions/model';
import { TapMissionView } from '../features/results/TapMission';
import { SyncMissionView } from '../features/results/SyncMission';
import { ContentMissionView } from '../features/content/ContentMission';
import { FountainExperience } from './FountainExperience';
import { createExperience, demoPeers } from './experience-store';
import type { Experience } from './experience-store';
import '../styles/global.css';
import '../styles/friendly.css';
import '../styles/polish.css';
import '../styles/account.css';
import './experience.css';

function ProfileDemo({store}:{store:Experience}) {
  const [nickname,setNickname]=useState(store.profile.nickname);const [bio,setBio]=useState(store.profile.bio);const [interests,setInterests]=useState<string[]>(store.profile.interests);const [accepted,setAccepted]=useState(false);const [error,setError]=useState('');
  return <div className="demo-profile"><form onSubmit={e=>{e.preventDefault();try{store.saveProfile(nickname,bio,interests,accepted);setError('');}catch(e){setError((e as Error).message);}}} noValidate>
    <Field label="닉네임" value={nickname} maxLength={30} onChange={e=>setNickname(e.target.value)}/><label htmlFor="demo-bio">한 줄 소개</label><textarea id="demo-bio" rows={3} maxLength={200} value={bio} onChange={e=>setBio(e.target.value)}/><fieldset><legend>관심 분야 · 1개 이상</legend>{['Python','디자인','음악','산책','독서'].map(x=><label className="content-choice" key={x}><input type="checkbox" checked={interests.includes(x)} onChange={()=>setInterests(interests.includes(x)?interests.filter(v=>v!==x):[...interests,x])}/>{x}</label>)}</fieldset><label className="content-choice"><input type="checkbox" checked={accepted} onChange={e=>setAccepted(e.target.checked)}/>가상 공개 안내를 확인했어요 (실제 동의 아님)</label><Button type="submit">가상 프로필 저장하기</Button>{store.progress.SHOW_ME==='COMPLETED'&&<p role="status" className="content-status">첫 번째 S를 모았어요!</p>}{error&&<p role="alert" className="form-notice is-error">{error}</p>}</form>
  </div>;
}
export function ExperienceApp() {
  const [store,setStore]=useState(()=>createExperience(()=>new Promise(resolve=>setTimeout(resolve,180))));useSyncExternalStore(store.subscribe,store.snapshot,store.snapshot);
  const [page,setPage]=useState('home');const [generation,setGeneration]=useState(0);const [peer,setPeer]=useState(91);const [notice,setNotice]=useState('');
  const mission=missions.find(m=>m.code===page);const data={preview:true,nickname:store.profile.nickname,progress:store.progress};
  const reset=()=>{setStore(createExperience(()=>new Promise(resolve=>setTimeout(resolve,180))));setGeneration(generation+1);setPage('home');setNotice('가상 데이터만 처음 상태로 돌렸어요.');};
  const refreshUi=async()=>{setGeneration(x=>x+1);await store.refresh();};
  function follow(path:string){const found=missions.find(m=>path===`/missions/${m.slug}`);setPage(found?found.code:path.startsWith('/discovery/profiles/')?'peer':path.startsWith('/discovery')?'discovery':path==='/complete'?'complete':path==='/missions'?'overview':'home');}
  return <main className="participant-shell demo-shell" onClickCapture={event=>{const target=(event.target as Element).closest('a');if(target){event.preventDefault();event.stopPropagation();follow(target.getAttribute('href')||'/');}}}>
    <div className="demo-banner">개발용 미리보기 · 실제 저장 없음</div>
    <header className="demo-header"><button className="demo-brand" onClick={()=>setPage('home')}>TAG ME<span>미션 놀이터</span></button><Button variant="quiet" onClick={reset}>가상 데이터 초기화</Button></header>
    <nav className="demo-nav" aria-label="체험 화면 선택"><button aria-current={page==='home'?'page':undefined} onClick={()=>setPage('home')}>수집판</button>{missions.map(m=><button key={m.code} aria-current={page===m.code?'page':undefined} onClick={()=>{setPage(m.code);setNotice('');}}>{m.name}</button>)}<button onClick={()=>setPage('discovery')}>동료 탐색</button></nav>
    <div className="demo-content"><p className="demo-reassurance">가상 NFC로 미션을 하나씩 열어 보세요. 모든 인물·소원·결과는 가상이며 새로 열면 초기화돼요.</p>
      {notice&&<p role="status" className="form-notice">{notice}</p>}
      {page==='home'&&<><div className="demo-welcome"><p className="context-label">엘리베이터를 기다리는 짧은 틈</p><h1>태그로 시작하는 연결</h1><p>태그 한 번, 우리 사이 한 걸음.</p><Button onClick={()=>setPage('auth')}>처음부터 체험하기</Button></div><Collection key={generation} data={data} celebrations={store.celebrations}/><EventEncouragement/><h2>SSAFY 미션</h2><MissionCards data={data}/></>}
      {page==='overview'&&<section><h1>미션 현황·힌트</h1><p>실제 위치는 확정하지 않았어요. 이 화면의 상태는 모두 가상이에요.</p><div className="demo-peer-list">{missions.map(m=><button key={m.code} onClick={()=>setPage(m.code)}><h2>{m.name}</h2><p>{store.progress[m.code]}</p><p>{m.code==='SHOW_ME'?'프로필 완성으로 첫 S를 모아요.':'실제 참여에는 현장 NFC·QR이 필요해요.'}</p></button>)}</div></section>}
      {page==='auth'&&<section className="demo-auth"><h1>반가워요, SSAFY 16기!</h1><p>실제 가입은 Mattermost 인증과 TAG ME 비밀번호가 필요해요. 여기서는 인증정보를 입력하지 않아요.</p><Button onClick={()=>setPage('SHOW_ME')}>가상 로그인 완료하고 프로필로</Button></section>}
      {mission&&<><header className="demo-mission-heading"><LetterSticker code={mission.code}/><div><h1>{mission.name}</h1><p>{mission.description}</p><p>{mission.encouragement}</p></div></header>
        <details className="demo-controls"><summary>개발용 상태·오류 체험</summary><div className="demo-actions"><label>가상 미션 상태<select value={store.progress[mission.code]} onChange={e=>{store.scenario(mission.code,e.target.value as MissionState);setGeneration(generation+1);}}>{(['LOCKED','UNLOCKED',...(page==='FAST_TAP'?['PENDING']:[]),'COMPLETED'] as MissionState[]).map(x=><option key={x} value={x}>{x}</option>)}</select></label><Button variant="quiet" onClick={()=>{store.fail();setNotice('다음 가상 작업에 오류를 표시해요. 실제 API는 호출하지 않아요.');}}>다음 작업 오류</Button>{['MAKE_A_WISH','SAY_SOMETHING'].includes(page)&&<><Button variant="quiet" onClick={()=>{store.moderate(page==='MAKE_A_WISH'?'wishes':'cheers','APPROVED');void refreshUi();}}>가상 승인</Button><Button variant="quiet" onClick={()=>{store.moderate(page==='MAKE_A_WISH'?'wishes':'cheers','REJECTED');void refreshUi();}}>가상 반려</Button><Button variant="quiet" onClick={()=>{store.emptyFeed(page==='MAKE_A_WISH'?'wishes':'cheers');void refreshUi();}}>빈 목록</Button></>}</div><p>운영자 판정이 아니에요. 승인·완료 전환은 메모리에서만 적용돼요.</p></details>
        <div key={`${page}-${generation}`} className="demo-mission-body">{store.progress[mission.code]==='LOCKED'&&page!=='SHOW_ME'?<div className="demo-locked"><LetterSticker code={mission.code}/><h2>아직 열리지 않은 미션이에요</h2><p>실제 서비스에서는 NFC·QR이 필요해요. 체험에서만 가상 해금해 보세요.</p><Button onClick={()=>{if(mission.code!=='SHOW_ME')store.unlockTag(mission.code);}}>가상 NFC 태그 찍기</Button></div>:<>{page==='SHOW_ME'&&<ProfileDemo store={store}/>} {page==='SYNC_ME'&&<><p className="form-notice">검토용 초안 6문항 · 운영 설문 미확정 · 가상 결과</p><SyncMissionView api={store.results} refresh={store.refresh} stepwise/></>}{page==='MAKE_A_WISH'&&<FountainExperience store={store}/>} {page==='FAST_TAP'&&<><p className="form-notice">가상 공식/연습 모두 실제 기회를 소모하지 않아요. 다시 연습하거나 데이터 초기화로 반복할 수 있어요.</p><TapMissionView api={store.results} refresh={store.refresh}/></>}{page==='SAY_SOMETHING'&&<ContentMissionView kind="cheers" api={store.content} reactions={store.reactions} refresh={store.refresh}/>}</>}</div>
        <Button variant="quiet" onClick={()=>setPage('home')}>수집판으로 돌아가기</Button></>}
      {page==='discovery'&&<section><h1>나와 통하는 동료</h1><p>가상 추천 · 실제 추천 점수나 관계 그래프를 변경하지 않아요.</p><div className="demo-peer-list">{demoPeers.map(p=><button key={p.id} onClick={()=>{setPeer(p.id);setPage('peer');}}><span className="demo-avatar">{p.nickname.slice(-2,-1)}</span><h2>{p.nickname}</h2><p>{p.interests.join(' · ')}</p><small>{p.interests.some(x=>store.profile.interests.includes(x))?'공통 관심사 · '+p.interests.filter(x=>store.profile.interests.includes(x)).join(', '):'같은 검토용 설문 버전의 가상 동료'}</small></button>)}</div></section>}
      {page==='peer'&&<section className="demo-profile-card"><p className="context-label">가상 동료 소개</p><h1>{demoPeers.find(p=>p.id===peer)?.nickname}</h1><p>{demoPeers.find(p=>p.id===peer)?.bio}</p><p>{demoPeers.find(p=>p.id===peer)?.interests.join(' · ')}</p><Button onClick={()=>setPage('discovery')}>추천 동료로 돌아가기</Button></section>}
      {page==='complete'&&<section className="demo-finale"><h1>{missions.every(m=>store.progress[m.code]==='COMPLETED')?'우리의 SSAFY가 모였어요!':'한 글자씩, 우리 사이 한 걸음'}</h1><Collection data={data}/><p>가상 수집 기록이에요. 실제 완료·시상·글자 지급은 없어요.</p><Button onClick={()=>setPage('discovery')}>동료 만나러 가기</Button></section>}
    </div><footer className="demo-footer">TAG ME · SSAFY 16기 네트워킹 이벤트<br/>기획·제작 | 서울 지역대표 이다연 · 백지원</footer>
  </main>;
}
if(import.meta.env.DEV && typeof document !== 'undefined')createRoot(document.getElementById('root')!).render(<ExperienceApp/>);
