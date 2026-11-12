import { useEffect, useState } from 'react';
import { LetterSticker } from '../components/LetterSticker';
import { WinterDecor } from '../components/WinterDecor';
import { ConnectionStickers } from '../components/EventArtwork';
import { useParticipation } from '../features/participation/context';
import type { CollectionCelebrations } from '../features/participation/collection-celebrations';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { Button, Dialog } from '../components/ui';
import { missions, stateLabels } from '../features/missions/model';
import type { MissionCode, MissionState, ScreenData } from '../features/missions/model';

const slotLabels: Record<MissionCode, string> = { SHOW_ME: '소개', SYNC_ME: '취향', MAKE_A_WISH: '소원', FAST_TAP: '탭', SAY_SOMETHING: '응원' };

const missionStatusLabel = (state: MissionState, code?: MissionCode) => code === 'SHOW_ME' && state === 'LOCKED' ? '프로필을 완성해 주세요' : stateLabels[state];

export function StateBadge({ state, missionCode }: { state: MissionState; missionCode?: MissionCode }) {
  const inProgress = state === 'PENDING' && !!missionCode && !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(missionCode);
  return <span className={`state-badge state-${inProgress ? 'in-progress' : state.toLowerCase()}`}><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : missionCode === 'SHOW_ME' ? 'person' : 'lock'} />{missionStatusLabel(state, missionCode)}</span>;
}

export function collectionVisual(code: MissionCode, state: MissionState, known = true) {
  if (!known || state === 'LOCKED') return 'undiscovered';
  if (state === 'COMPLETED') return 'collected';
  return code === 'SHOW_ME' ? 'undiscovered' : 'discovered';
}

export function Collection({ data, celebrations }: { data: ScreenData; celebrations?: CollectionCelebrations | null }) {
  const known = !!(data.preview || data.serverLoaded);
  const completed = known ? missions.filter((mission) => data.progress[mission.code] === 'COMPLETED').length : 0;
  const complete = completed === missions.length;
  const [earned, setEarned] = useState<MissionCode[]>([]);
  const [completionOpen, setCompletionOpen] = useState(false);

  useEffect(() => {
    if (!known || !celebrations) return;
    const collected = missions.filter(mission => data.progress[mission.code] === 'COMPLETED').map(mission => mission.code);
    const next = celebrations.takeLetters(collected);
    if (next.length) setEarned(next);
  }, [celebrations, data.progress, known]);

  useEffect(() => {
    if (!earned.length) return;
    const timer = window.setTimeout(() => setEarned([]), 3600);
    return () => window.clearTimeout(timer);
  }, [earned]);

  useEffect(() => {
    if (!complete || !celebrations) return;
    const timer = window.setTimeout(() => {
      if (celebrations.takeCompletion()) setCompletionOpen(true);
    }, 620);
    return () => window.clearTimeout(timer);
  }, [celebrations, complete]);

  const newLetters = missions.filter(mission => earned.includes(mission.code)).map(mission => mission.letter);
  const notice = !newLetters.length ? '' : newLetters.length === 1 ? `${newLetters[0]}를 획득했어요!` : `${newLetters.join(' · ')} 글자를 획득했어요!`;

  return <section className={`collection-panel sticker-board${complete && earned.length ? ' collection-celebrating' : ''}`} aria-labelledby="collection-heading">
    <WinterDecor /><div className="panel-heading"><h2 id="collection-heading">나의 SSAFY 수집판</h2>{(data.preview || data.serverLoaded) && <span className="collection-count"><b>{completed}</b> / 5 획득</span>}</div>
    {completed < 5 && <div className="collection-invitation"><span className="collection-bubble" aria-hidden="true">우리를 찾아봐!</span></div>}
    <div className="letter-slots">{missions.map((mission) => {
      const state = known ? data.progress[mission.code] : 'LOCKED'; const visual = collectionVisual(mission.code, state, known);
      return <Link key={mission.code} data-mission={mission.code} href={`/missions/${mission.slug}`} data-visual={visual} className={`letter-slot ${visual === 'collected' ? 'is-collected' : ''}`} title={mission.name} aria-label={`${mission.letter}, ${mission.name}, ${data.preview || data.serverLoaded ? missionStatusLabel(state, mission.code) : '미션 알아보기'}`}>
        <LetterSticker code={mission.code} collectionState={state === 'COMPLETED' ? 'collected' : 'uncollected'} celebrate={earned.includes(mission.code)} /><span className="slot-name">{mission.letter}</span><span className="slot-label">{slotLabels[mission.code]}</span><span className="slot-state"><Icon size={14} name={!data.preview && !data.serverLoaded ? 'tag' : state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : mission.code === 'SHOW_ME' ? 'person' : 'lock'} /></span>
      </Link>;
    })}</div>
    {(data.preview || data.serverLoaded) && <div className="collection-progress" role="progressbar" aria-label={data.preview ? '미리보기 글자 수집' : '나의 글자 수집'} aria-valuenow={completed} aria-valuemin={0} aria-valuemax={5}><span style={{ width: `${completed * 20}%` }} /></div>}
    <p className="collection-caption"><strong className="collection-earned" role="status">{notice}</strong>{!notice && (complete ? 'SSAFY 글자를 모두 모았어요.' : '획득한 글자는 여기에 모여요.')}</p>
    {complete && <Link className="text-link collection-complete-link" href="/complete">완성 기록 보기<Icon name="arrow" size={16} /></Link>}
    {/* taste-skill-ignore Keep the user's approved full-collection congratulation message verbatim. */}{celebrations && <Dialog open={completionOpen} title="SSAFY 글자를 모두 수집했어요! 축하드려요 🎉" onClose={() => setCompletionOpen(false)}>
      <p>완성 기록은 수집판에서 다시 볼 수 있어요.</p>
      <Button type="button" onClick={() => setCompletionOpen(false)}>확인했어요</Button>
    </Dialog>}
  </section>;
}

export function Home({ data }: { data: ScreenData }) {
  const auth = useAuth();
  const participation = useParticipation();
  return <div className="page home-page participant-page">
    <div className="page-heading home-welcome"><div><h1>태그를 찾고, SSAFY를 모아요</h1><p>곳곳의 NFC 태그를 찾아 미션을 완료하고, 새로운 동료를 만나보세요.</p></div><div className="home-artwork"><ConnectionStickers className="welcome-stickers" /><WinterDecor /></div></div>
    {!data.preview && auth.user && participation.error && <div className="collection-error" role="alert"><p>{participation.error}</p><button className="text-button" onClick={() => void participation.refresh()}>기록 다시 불러오기</button></div>}
    <div className="home-overview" aria-busy={participation.loading}><Collection key={data.preview ? 'preview' : auth.user?.id ?? 'guest'} data={data} celebrations={data.preview ? null : participation.celebrations} />

    </div>
    <EventEncouragement />
    <section className="mission-section" aria-labelledby="mission-heading"><div className="section-heading"><h2 id="mission-heading">SSAFY 미션</h2><span>어떤 미션부터 알아볼까요?</span></div>
      <MissionCards data={data} />

    </section>
  </div>;
}

export function MissionCards({ data }: { data: ScreenData }) {
  return <div className="mission-list">{missions.map((mission) => <Link key={mission.code} href={`/missions/${mission.slug}`} className="mission-row" data-visual={collectionVisual(mission.code, data.progress[mission.code], !!(data.preview || data.serverLoaded))} data-mission={mission.code} aria-label={`${mission.name}, ${mission.description}, 상세 안내`}><span className={`mission-letter ${data.progress[mission.code] === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true"><LetterSticker code={mission.code} collectionState={collectionVisual(mission.code, data.progress[mission.code], !!(data.preview || data.serverLoaded)) === 'collected' ? 'collected' : 'uncollected'} /></span><div className="mission-row-copy"><h3>{mission.name}</h3><p>{mission.description}</p></div><div className="mission-row-state">{(data.preview || data.serverLoaded) && <StateBadge state={data.progress[mission.code]} missionCode={mission.code} />}<Icon name="arrow" size={18} /></div></Link>)}</div>;
}

const encouragementMessages=['미션을 열심히 하는 자에게 행운이!','친구들의 프로필을 탐색하며 친밀감을 쌓아봐!','참여 자체가 행운이 될 수도 있어.'];
export function EventEncouragement() {
  const [index,setIndex]=useState(0),[hovered,setHovered]=useState(false),[focused,setFocused]=useState(false),[hidden,setHidden]=useState(false);
  useEffect(()=>{
    const changed=()=>setHidden(document.hidden);changed();
    document.addEventListener('visibilitychange',changed);
    return()=>document.removeEventListener('visibilitychange',changed);
  },[]);
  useEffect(()=>{
    if(hovered||focused||hidden)return;
    const timer=window.setInterval(()=>setIndex(previous=>(previous+1)%encouragementMessages.length),3000);
    return()=>window.clearInterval(timer);
  },[hovered,focused,hidden]);
  return <div className="event-encouragement" role="region" aria-label="전체 미션 힌트" tabIndex={0} onPointerEnter={event=>{if(event.pointerType==='mouse')setHovered(true);}} onPointerLeave={()=>setHovered(false)} onFocus={()=>setFocused(true)} onBlur={()=>setFocused(false)}><p aria-live="off">{encouragementMessages[index]}</p></div>;
}
