import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { missions, stateLabels } from '../features/missions/model';
import type { MissionState, ScreenData } from '../features/missions/model';

export function StateBadge({ state }: { state: MissionState }) {
  return <span className={`state-badge state-${state.toLowerCase()}`}><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} />{stateLabels[state]}</span>;
}

export function Collection({ data }: { data: ScreenData }) {
  const completed = missions.filter((mission) => data.progress[mission.code] === 'COMPLETED').length;
  return <section className="collection-panel" aria-labelledby="collection-heading">
    <div className="panel-heading"><h2 id="collection-heading">나의 SSAFY 수집판</h2><span className="collection-count">{data.preview ? <><b>{completed}</b> / 5 획득</> : '로그인 후 기록 확인'}</span></div>
    <div className="letter-slots">{missions.map((mission) => {
      const state = data.progress[mission.code];
      return <Link key={mission.code} href={`/missions/${mission.slug}`} className={`letter-slot ${state === 'COMPLETED' ? 'is-collected' : ''}`} aria-label={`${mission.letter}, ${mission.name}, ${data.preview ? stateLabels[state] : '미션 안내 보기'}`}>
        <strong>{mission.letter}</strong><span className="slot-name">{mission.code === 'SHOW_ME' || mission.code === 'SYNC_ME' ? mission.name : mission.korean}</span><span className="slot-state"><Icon size={14} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} /></span>
      </Link>;
    })}</div>
    {data.preview && <div className="collection-progress" role="progressbar" aria-label="미리보기 글자 수집" aria-valuenow={completed} aria-valuemin={0} aria-valuemax={5}><span style={{ width: `${completed * 20}%` }} /></div>}
    <p className="collection-caption">{data.preview ? completed === 5 ? '다섯 글자를 모두 모은 화면의 미리보기예요.' : '미션에 참여하고, 다섯 글자를 하나씩 모아보세요.' : '다섯 미션을 먼저 둘러보세요. 수집 기록은 로그인 후 표시돼요.'}</p>
    {data.preview && completed === 5 && <Link className="text-link" href="/complete">완성 화면 보기<Icon name="arrow" size={16} /></Link>}
  </section>;
}

export function Home({ data }: { data: ScreenData }) {
  const pending = missions.filter((mission) => data.progress[mission.code] === 'PENDING').length;
  const next = missions.find((mission) => data.progress[mission.code] === 'UNLOCKED');
  return <div className="page home-page">
    <div className="page-heading"><div><p className="context-label">SSAFY 16기 · 네트워킹</p><h1>{data.nickname ? `${data.nickname}님의 미션` : '다섯 미션으로 시작하는 연결'}</h1><p>나를 소개하고, 취향을 발견하고, 우리의 SSAFY를 완성해요.</p></div></div>
    <div className="home-overview"><Collection data={data} />
      <aside className="next-panel" aria-labelledby="next-heading"><h2 id="next-heading">{data.preview ? next ? next.korean : pending ? '글을 확인하고 있어요' : '현장 태그를 찾아보세요' : '현장에서 태그를 만나면'}</h2><p>{data.preview ? next ? `${next.name} 미션에 참여할 수 있어요. 지금 이어서 둘러보세요.` : pending ? `${pending}개 미션이 검수 대기 중이에요. 승인 후 글자가 지급돼요.` : 'NFC를 읽거나 QR을 스캔하면 해당 미션으로 이어져요.' : 'NFC 또는 QR로 미션을 열고, 참여를 마치면 글자를 모을 수 있어요.'}</p><Link className="button button--primary" href={data.preview ? next ? `/missions/${next.slug}` : '/missions/make-a-wish' : '/auth'}>{data.preview ? next ? '미션 이어보기' : '미션 안내 보기' : '로그인 화면으로'}<Icon name="arrow" size={18} /></Link><span className="next-footnote">{data.preview ? '개발용 상태 · 실제 참여 기록 없음' : 'Mattermost 아이디 · TAG ME 비밀번호'}</span></aside>
    </div>
    {!data.preview && <div className="preparation-note" role="status"><Icon name="clock" size={18} /><p><b>참여 기능 준비 중</b>지금은 화면과 미션 안내를 둘러볼 수 있어요. 인증과 참여는 아직 연결되지 않았어요.</p></div>}
    <section className="mission-section" aria-labelledby="mission-heading"><div className="section-heading"><h2 id="mission-heading">다섯 미션</h2><span>순서는 자유롭게</span></div>
      <div className="mission-list">{missions.map((mission) => <Link key={mission.code} href={`/missions/${mission.slug}`} className="mission-row"><span className={`mission-letter ${data.progress[mission.code] === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true">{mission.letter}</span><div className="mission-row-copy"><h3>{mission.name}<span>{mission.korean}</span></h3><p>{mission.description}</p></div><div className="mission-row-state">{data.preview ? <StateBadge state={data.progress[mission.code]} /> : <span className="mission-link-label">미션 안내</span>}<Icon name="arrow" size={18} /></div></Link>)}</div>
    </section>
    <section className="home-help" aria-labelledby="how-heading"><div><h2 id="how-heading">참여 전에 알아두세요</h2><p>태그 찾기 → 미션 참여 → 글자 수집. 소원과 응원은 검수 승인 후 글자가 지급돼요.</p></div><Link className="text-link" href="/discovery">동료 찾기 안내<Icon name="arrow" size={16} /></Link></section>
  </div>;
}
