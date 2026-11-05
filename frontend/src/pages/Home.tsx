import { HomeNetwork } from '../features/discovery/HomeNetwork';
import { useParticipation } from '../features/participation/context';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { missions, stateLabels } from '../features/missions/model';
import type { MissionCode, MissionState, ScreenData } from '../features/missions/model';

const slotLabels: Record<MissionCode, string> = { SHOW_ME: 'SHOW ME', SYNC_ME: 'SYNC ME', MAKE_A_WISH: '소원', FAST_TAP: '탭 게임', SAY_SOMETHING: '응원' };

export function StateBadge({ state }: { state: MissionState }) {
  return <span className={`state-badge state-${state.toLowerCase()}`}><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} />{stateLabels[state]}</span>;
}

export function Collection({ data }: { data: ScreenData }) {
  const auth = useAuth();
  const completed = missions.filter((mission) => data.progress[mission.code] === 'COMPLETED').length;
  const pending = missions.filter((mission) => data.progress[mission.code] === 'PENDING').length;
  return <section className="collection-panel" aria-labelledby="collection-heading">
    <div className="panel-heading"><h2 id="collection-heading">나의 SSAFY 수집판</h2><span className="collection-count">{data.preview || data.serverLoaded ? <><b>{completed}</b> / 5 획득</> : auth.user ? '기록 확인 중' : '로그인 후 기록 확인'}</span></div>
    <div className="letter-slots">{missions.map((mission) => {
      const state = data.progress[mission.code];
      return <Link key={mission.code} href={`/missions/${mission.slug}`} className={`letter-slot ${state === 'COMPLETED' ? 'is-collected' : ''}`} aria-label={`${mission.letter}, ${mission.name}, ${data.preview || data.serverLoaded ? stateLabels[state] : '미션 안내 보기'}`}>
        <strong>{mission.letter}</strong><span className="slot-name">{slotLabels[mission.code]}</span><span className="slot-state"><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} /></span>
      </Link>;
    })}</div>
    {(data.preview || data.serverLoaded) && <div className="collection-progress" role="progressbar" aria-label={data.preview ? '미리보기 글자 수집' : '나의 글자 수집'} aria-valuenow={completed} aria-valuemin={0} aria-valuemax={5}><span style={{ width: `${completed * 20}%` }} /></div>}
    <p className="collection-caption">{data.preview || data.serverLoaded ? completed === 5 ? data.preview ? '다섯 글자를 모두 모은 화면의 미리보기예요.' : '다섯 글자를 모두 모았어요. 완료 기록을 확인해요.' : pending ? `${pending}개 미션 검수 중 · 승인 후 수집판에 반영돼요.` : '미션에 참여하고, 다섯 글자를 하나씩 모아보세요.' : auth.user ? '계정 연결을 마쳤어요. 미션 참여와 기록은 준비하고 있어요.' : '다섯 미션을 먼저 둘러보세요. 수집 기록은 로그인 후 표시돼요.'}</p>
  </section>;
}

export function Home({ data }: { data: ScreenData }) {
  const auth = useAuth();
  const participation = useParticipation();
  const pendingMission = missions.find((mission) => data.progress[mission.code] === 'PENDING');
  const next = missions.find((mission) => data.progress[mission.code] === 'UNLOCKED');
  const complete = missions.every((mission) => data.progress[mission.code] === 'COMPLETED');
  let followUp = { title: '현장에서 태그를 만나면', description: 'NFC 또는 QR로 미션을 열고, 참여를 마치면 글자를 모을 수 있어요.', href: '/auth', action: '로그인 화면으로' };
  if (auth.user && !data.preview) followUp = { title: '계정 연결을 마쳤어요', description: auth.user.onboardingRequired ? '프로필과 공개 범위를 정하고 첫인사를 준비해요.' : '내 계정에서 프로필과 비밀번호를 관리할 수 있어요.', href: auth.user?.onboardingRequired ? '/me/profile' : '/me', action: auth.user?.onboardingRequired ? '프로필 등록하기' : '내 계정 확인하기' };
  if (data.preview || data.serverLoaded) {
    if (complete) followUp = { title: '다섯 연결을 완성했어요', description: '글자 수집을 마친 화면이에요. 완성 화면을 확인해 보세요.', href: '/complete', action: '완성 화면 보기' };
    else if (next) followUp = { title: next.korean, description: `${next.name} 미션에 참여할 수 있어요. 지금 이어서 둘러보세요.`, href: `/missions/${next.slug}`, action: '미션 이어보기' };
    else if (pendingMission) followUp = { title: '글을 확인하고 있어요', description: '아직 글자가 지급되지 않았어요. 검수 상태는 미션 화면에서 확인해요.', href: `/missions/${pendingMission.slug}`, action: '검수 상태 보기' };
    else followUp = { title: '현장 태그를 찾아보세요', description: 'NFC를 읽거나 QR을 스캔하면 해당 미션으로 이어져요.', href: '/missions/show-me', action: '미션 안내 보기' };
  }
  return <div className="page home-page">
    <div className="page-heading"><div><p className="context-label">SSAFY 16기 · 네트워킹</p><h1>{data.serverLoaded ? '나의 미션 수집판' : '다섯 미션으로 시작하는 연결'}</h1><p>나를 소개하고, 취향을 발견하고, 우리의 SSAFY를 완성해요.</p></div></div>
    {!data.preview && auth.user && participation.error && <div className="collection-error" role="alert"><p>{participation.error}</p><button className="text-button" onClick={() => void participation.refresh()}>기록 다시 불러오기</button></div>}
    <div className="home-overview" aria-busy={participation.loading}><Collection data={data} />
      <aside className="next-panel" aria-labelledby="next-heading"><h2 id="next-heading">{followUp.title}</h2><p>{followUp.description}</p><Link className="button button--primary" href={followUp.href}>{followUp.action}<Icon name="arrow" size={18} /></Link><span className="next-footnote">{data.preview ? '개발용 상태 · 실제 참여 기록 없음' : 'Mattermost 아이디 · TAG ME 비밀번호'}</span></aside>
    </div>
    {!data.preview && !data.serverLoaded && <div className="preparation-note" role="status"><Icon name="clock" size={18} /><p><b>참여 기능 준비 중</b>미션 안내를 둘러볼 수 있어요. 성향·소원·탭·응원 참여는 순서대로 연결하고 있어요.</p></div>}
    <section className="mission-section" aria-labelledby="mission-heading"><div className="section-heading"><h2 id="mission-heading">다섯 미션</h2><span>순서는 자유롭게</span></div>
      <div className="mission-list">{missions.map((mission) => <Link key={mission.code} href={`/missions/${mission.slug}`} className="mission-row"><span className={`mission-letter ${data.progress[mission.code] === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true">{mission.letter}</span><div className="mission-row-copy"><h3>{mission.name}<span>{mission.korean}</span></h3><p>{mission.description}</p></div><div className="mission-row-state">{data.preview || data.serverLoaded ? <StateBadge state={data.progress[mission.code]} /> : <span className="mission-link-label">미션 안내</span>}<Icon name="arrow" size={18} /></div></Link>)}</div>
    </section>
    {!data.preview && auth.user && !auth.user.onboardingRequired && <HomeNetwork key={auth.user.id} />}
    <section className="home-help" aria-labelledby="how-heading"><div><h2 id="how-heading">참여 전에 알아두세요</h2><p>태그 찾기 → 미션 참여 → 글자 수집. 소원과 응원은 검수 승인 후 글자가 지급돼요.</p></div><Link className="text-link" href="/discovery">동료 찾기 안내<Icon name="arrow" size={16} /></Link></section>
  </div>;
}
