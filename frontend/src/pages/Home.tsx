import { useState } from 'react';
import { LetterSticker } from '../components/LetterSticker';
import { WinterDecor } from '../components/WinterDecor';
import { ConnectionStickers } from '../components/EventArtwork';
import { useParticipation } from '../features/participation/context';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { missions, stateLabels } from '../features/missions/model';
import type { MissionCode, MissionState, ScreenData } from '../features/missions/model';

const slotLabels: Record<MissionCode, string> = { SHOW_ME: '소개', SYNC_ME: '취향', MAKE_A_WISH: '소원', FAST_TAP: '탭', SAY_SOMETHING: '응원' };

/** SHOW ME has no NFC unlock step; only its completed state reveals color. */
export function collectionVisual(code: MissionCode, state: MissionState, known: boolean): 'unrevealed' | 'unlocked' | 'collected' {
  if (!known || state === 'LOCKED') return 'unrevealed';
  if (state === 'COMPLETED') return 'collected';
  return code === 'SHOW_ME' ? 'unrevealed' : 'unlocked';
}

const missionStatusLabel = (state: MissionState, code?: MissionCode) => code === 'SHOW_ME' && state === 'LOCKED' ? '프로필을 완성해 주세요' : state === 'PENDING' && !!code && !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(code) ? '진행 중이에요' : stateLabels[state];

export function StateBadge({ state, missionCode }: { state: MissionState; missionCode?: MissionCode }) {
  const inProgress = state === 'PENDING' && !!missionCode && !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(missionCode);
  return <span className={`state-badge state-${inProgress ? 'in-progress' : state.toLowerCase()}`}><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : missionCode === 'SHOW_ME' ? 'person' : 'lock'} />{missionStatusLabel(state, missionCode)}</span>;
}

export function Collection({ data }: { data: ScreenData }) {
  const known = !!(data.preview || data.serverLoaded);
  const completed = known ? missions.filter((mission) => data.progress[mission.code] === 'COMPLETED').length : 0;
  return <section className="collection-panel sticker-board" aria-labelledby="collection-heading">
    <WinterDecor /><div className="panel-heading"><h2 id="collection-heading">나의 SSAFY 수집판</h2>{(data.preview || data.serverLoaded) && <span className="collection-count"><b>{completed}</b> / 5 획득</span>}</div>
    {completed < 5 && <div className="collection-invitation"><span className="collection-bubble" aria-hidden="true">우리를 찾아봐!</span></div>}
    <div className="letter-slots">{missions.map((mission) => {
      const state = known ? data.progress[mission.code] : 'LOCKED';
      const visual = collectionVisual(mission.code, state, known);
      return <Link key={mission.code} data-mission={mission.code} data-visual={visual} href={`/missions/${mission.slug}`} className={`letter-slot ${visual === 'collected' ? 'is-collected' : ''}`} title={mission.name} aria-label={`${mission.letter}, ${mission.name}, ${data.preview || data.serverLoaded ? missionStatusLabel(state, mission.code) : '미션 알아보기'}`}>
        <LetterSticker code={mission.code} collectionState={visual === 'collected' ? 'collected' : 'uncollected'} celebrate={!!data.serverLoaded && !data.preview} /><span className="slot-name">{mission.letter}</span><span className="slot-label">{slotLabels[mission.code]}</span><span className="slot-state"><Icon size={14} name={!data.preview && !data.serverLoaded ? 'tag' : state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : mission.code === 'SHOW_ME' ? 'person' : 'lock'} /></span>
      </Link>;
    })}</div>
    {(data.preview || data.serverLoaded) && <div className="collection-progress" role="progressbar" aria-label={data.preview ? '미리보기 글자 수집' : '나의 글자 수집'} aria-valuenow={completed} aria-valuemin={0} aria-valuemax={5}><span style={{ width: `${completed * 20}%` }} /></div>}
    <p className="collection-caption">획득한 글자는 여기에 모여요.</p>
    {(data.preview || data.serverLoaded) && completed === 5 && <Link className="text-link collection-complete-link" href="/complete">완성 기록 보기<Icon name="arrow" size={16} /></Link>}
  </section>;
}

export function Home({ data }: { data: ScreenData }) {
  const auth = useAuth();
  const participation = useParticipation();
  return <div className="page home-page participant-page">
    <div className="page-heading home-welcome"><div><p className="context-label">엘리베이터를 기다리는 짧은 틈, 새로운 동료를 만나요.</p><h1>태그로 시작하는 연결</h1><p>태그 한 번, 우리 사이 한 걸음.</p></div><div className="home-artwork"><ConnectionStickers className="welcome-stickers" /><WinterDecor /></div></div>
    {!data.preview && auth.user && participation.error && <div className="collection-error" role="alert"><p>{participation.error}</p><button className="text-button" onClick={() => void participation.refresh()}>기록 다시 불러오기</button></div>}
    <div className="home-overview" aria-busy={participation.loading}><Collection data={data} />

    </div>
    <EventEncouragement />
    <section className="mission-section" aria-labelledby="mission-heading"><div className="section-heading"><h2 id="mission-heading">SSAFY 미션</h2><span>어떤 미션부터 알아볼까요?</span></div>
      <MissionCards data={data} />
      <p className="home-policy-note">소원과 응원은 승인된 후 글자를 받아요.</p>

    </section>
  </div>;
}

export function MissionCards({ data }: { data: ScreenData }) {
  return <div className="mission-list">{missions.map((mission) => <Link key={mission.code} href={`/missions/${mission.slug}`} className="mission-row" data-mission={mission.code} aria-label={`${mission.name}, ${mission.description}, 상세 안내`}><span className={`mission-letter ${data.progress[mission.code] === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true"><LetterSticker code={mission.code} /></span><div className="mission-row-copy"><h3>{mission.name}</h3><p>{mission.description}</p></div><div className="mission-row-state">{(data.preview || data.serverLoaded) && <StateBadge state={data.progress[mission.code]} missionCode={mission.code} />}<Icon name="arrow" size={18} /></div></Link>)}</div>;
}

export function EventEncouragement() {
  const [index,setIndex] = useState(0);
  const messages=['미션을 열심히 하는 자에게 행운이!','친구들의 프로필을 탐색하며 친밀감을 쌓아봐!','참여 자체가 행운이 될 수도 있어.'];
  return <div className="event-encouragement"><p aria-live="polite">{messages[index]}</p><button className="text-button" onClick={()=>setIndex((index+1)%messages.length)}>다른 응원</button></div>;
}
