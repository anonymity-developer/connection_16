import { LetterSticker } from '../components/LetterSticker';
import { WinterDecor } from '../components/WinterDecor';
import { ConnectionStickers } from '../components/EventArtwork';
import { HomeNetwork } from '../features/discovery/HomeNetwork';
import { useParticipation } from '../features/participation/context';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { missions, stateLabels } from '../features/missions/model';
import type { MissionCode, MissionState, ScreenData } from '../features/missions/model';

const homeCopy: Record<MissionCode, { label: string; description: string }> = {
  SHOW_ME: { label: '소개', description: '나를 소개하고 첫인사를 나눠요.' },
  SYNC_ME: { label: '취향', description: '취향이 닮은 동료를 발견해요.' },
  MAKE_A_WISH: { label: '소원', description: '나의 소원을 남겨요.' },
  FAST_TAP: { label: '탭', description: '10초 동안 빠르게 탭해요!' },
  SAY_SOMETHING: { label: '응원', description: '동료에게 따뜻한 응원을 전해요.' },
};

const missionStatusLabel = (state: MissionState, code?: MissionCode) => state === 'PENDING' && !!code && !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(code) ? '진행 중이에요' : stateLabels[state];

export function StateBadge({ state, missionCode }: { state: MissionState; missionCode?: MissionCode }) {
  const inProgress = state === 'PENDING' && !!missionCode && !['MAKE_A_WISH', 'SAY_SOMETHING'].includes(missionCode);
  return <span className={`state-badge state-${inProgress ? 'in-progress' : state.toLowerCase()}`}><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} />{missionStatusLabel(state, missionCode)}</span>;
}

export function Collection({ data }: { data: ScreenData }) {
  const completed = missions.filter((mission) => data.progress[mission.code] === 'COMPLETED').length;
  return <section className="collection-panel sticker-board" aria-labelledby="collection-heading">
    <WinterDecor /><div className="panel-heading"><h2 id="collection-heading">나의 SSAFY 수집판</h2>{(data.preview || data.serverLoaded) && <span className="collection-count"><b>{completed}</b> / 5 획득</span>}</div>
    <div className="letter-slots">{missions.map((mission) => {
      const state = data.progress[mission.code];
      return <Link key={mission.code} data-mission={mission.code} href={`/missions/${mission.slug}`} className={`letter-slot ${state === 'COMPLETED' ? 'is-collected' : ''}`} title={mission.name} aria-label={`${mission.letter}, ${mission.name}, ${data.preview || data.serverLoaded ? missionStatusLabel(state, mission.code) : '미션 알아보기'}`}>
        <LetterSticker code={mission.code} collectionState={state === 'COMPLETED' ? 'collected' : 'uncollected'} celebrate={!!data.serverLoaded && !data.preview} /><span className="slot-name">{mission.letter}</span><span className="slot-label">{homeCopy[mission.code].label}</span><span className="slot-state"><Icon size={14} name={!data.preview && !data.serverLoaded ? 'tag' : state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} /></span>
      </Link>;
    })}</div>
    {(data.preview || data.serverLoaded) && <div className="collection-progress" role="progressbar" aria-label={data.preview ? '미리보기 글자 수집' : '나의 글자 수집'} aria-valuenow={completed} aria-valuemin={0} aria-valuemax={5}><span style={{ width: `${completed * 20}%` }} /></div>}
    <p className="collection-caption">획득한 글자는 여기에 모여요.</p>
  </section>;
}

export function Home({ data }: { data: ScreenData }) {
  const auth = useAuth();
  const participation = useParticipation();
  const pendingMission = missions.find((mission) => data.progress[mission.code] === 'PENDING');
  const next = missions.find((mission) => data.progress[mission.code] === 'UNLOCKED');
  const complete = missions.every((mission) => data.progress[mission.code] === 'COMPLETED');
  let followUp = { title: '첫 태그, 함께 시작해요', description: '로그인하고 나만의 SSAFY를 모아보세요.', href: '/auth', action: '로그인하고 시작하기' };
  if (auth.user && !data.preview) followUp = { title: '계정 연결을 마쳤어요', description: auth.user.onboardingRequired ? '프로필과 공개 범위를 정하고 첫인사를 준비해요.' : '내 계정에서 프로필과 비밀번호를 관리할 수 있어요.', href: auth.user?.onboardingRequired ? '/me/profile' : '/me', action: auth.user?.onboardingRequired ? '프로필 등록하기' : '내 계정 확인하기' };
  if (data.preview || data.serverLoaded) {
    if (complete) followUp = { title: '다섯 연결을 완성했어요', description: '글자 수집을 마친 화면이에요. 완성 화면을 확인해 보세요.', href: '/complete', action: '완성 화면 보기' };
    else if (next) followUp = { title: next.korean, description: `${next.name} 미션에 참여할 수 있어요. 지금 이어서 둘러보세요.`, href: `/missions/${next.slug}`, action: '미션 알아보기' };
    else if (pendingMission) followUp = ['MAKE_A_WISH', 'SAY_SOMETHING'].includes(pendingMission.code)
      ? { title: '글을 확인하고 있어요', description: '아직 글자가 지급되지 않았어요. 검수 상태는 미션 화면에서 확인해요.', href: `/missions/${pendingMission.slug}`, action: '검수 상태 보기' }
      : { title: '참여 중인 미션이 있어요', description: `${pendingMission.name} 화면에서 진행 상태와 저장된 기록을 확인해요.`, href: `/missions/${pendingMission.slug}`, action: '진행 상태 보기' };
    else followUp = { title: '현장 태그를 찾아보세요', description: 'NFC를 읽거나 QR을 스캔하면 해당 미션으로 이어져요.', href: '/missions/show-me', action: '미션 알아보기' };
  }
  return <div className="page home-page participant-page">
    <div className="page-heading home-welcome"><div><p className="context-label">반가워요, SSAFY 16기!</p><h1>태그로 시작하는 연결</h1><p>태그 한 번, 우리 사이 한 걸음.</p></div><div className="home-artwork"><ConnectionStickers className="welcome-stickers" /><WinterDecor /></div></div>
    {!data.preview && auth.user && participation.error && <div className="collection-error" role="alert"><p>{participation.error}</p><button className="text-button" onClick={() => void participation.refresh()}>기록 다시 불러오기</button></div>}
    <div className="home-overview" aria-busy={participation.loading}><Collection data={data} />
      <aside className={`next-panel ${!auth.user && !data.preview ? 'is-login-cta' : ''}`} aria-labelledby="next-heading"><h2 id="next-heading">{followUp.title}</h2><p>{followUp.description}</p><Link className="button button--primary" href={followUp.href}>{followUp.action}<Icon name="arrow" size={18} /></Link>{data.preview && <span className="next-footnote">개발용 상태 · 실제 참여 기록 없음</span>}</aside>
    </div>
    <section className="mission-section" aria-labelledby="mission-heading"><div className="section-heading"><h2 id="mission-heading">SSAFY 미션</h2><span>어떤 미션부터 알아볼까요?</span></div>
      <div className="mission-list">{missions.map((mission) => <Link key={mission.code} href={`/missions/${mission.slug}`} className="mission-row" data-mission={mission.code}><span className={`mission-letter ${data.progress[mission.code] === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true"><LetterSticker code={mission.code} /></span><div className="mission-row-copy"><h3>{mission.name}</h3><p>{homeCopy[mission.code].description}</p></div><div className="mission-row-state">{(data.preview || data.serverLoaded) && <StateBadge state={data.progress[mission.code]} missionCode={mission.code} />}<span className="mission-link-label">미션 알아보기</span><Icon name="arrow" size={18} /></div></Link>)}<Link href="/missions" className="mission-row mission-overview-card"><Icon name="sparkles" size={24} /><div className="mission-row-copy"><h3>미션 현황·힌트</h3><p>나의 진행 현황과 다음 한 걸음을 확인해요.</p></div><div className="mission-row-state"><span className="mission-link-label">현황·힌트 보기</span><Icon name="arrow" size={18} /></div></Link></div>

    </section>
    <p className="home-policy-note">소원·응원 미션은 검수 승인 후 글자를 받아요.</p>
    {!data.preview && auth.user && !auth.user.onboardingRequired && <HomeNetwork key={auth.user.id} />}
  </div>;
}
