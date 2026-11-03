import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { missions, stateLabels } from '../features/missions/model';
import type { ScreenData } from '../features/missions/model';

export function Collection({ data }: { data: ScreenData }) {
  const completed = missions.filter((mission) => data.progress[mission.code] === 'COMPLETED').length;
  return <section className="collection-ticket" aria-label="SSAFY 글자 수집판">
    <div className="ticket-heading"><span className="eyebrow">YOUR CONNECTION PASS</span><span className="ticket-no">NO. 016</span></div>
    <div className="ticket-title"><h2>{data.nickname ? '나의 연결 티켓' : '다섯 글자, 하나의 연결'}</h2><span><b>{completed}</b> / 5</span></div>
    <div className="letter-slots">{missions.map((mission, index) => {
      const state = data.progress[mission.code];
      return <Link key={mission.code} href={`/missions/${mission.slug}`} className={`letter-slot letter-slot--${mission.color} ${state === 'COMPLETED' ? 'is-collected' : ''}`} aria-label={`${mission.name}, ${stateLabels[state]}`}><span className="slot-index">0{index + 1}</span><strong>{mission.letter}</strong><span className="slot-state"><Icon size={12} name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'arrow' : 'lock'} /></span></Link>;
    })}</div>
    <div className="ticket-bottom"><div className="barcode" aria-hidden="true" /><p>작은 발견을 모아<br /><b>우리의 SSAFY를 완성해요.</b></p><span className="ticket-stamp">LET’S<br />CONNECT!</span></div>
  </section>;
}

export function Home({ data }: { data: ScreenData }) {
  const pending = missions.filter((mission) => data.progress[mission.code] === 'PENDING').length;
  return <div className="page home-page">
    <section className="hero">
      <div className="hero-copy"><div className="event-pill"><span />SSAFY 16TH · NETWORKING EVENT</div><h1>{data.nickname ? <>{data.nickname}님,<br />오늘도 <span className="underlined">연결해요.</span></> : <>우리, 한 번<br /><span className="underlined">연결돼</span> 볼까요?</>}</h1><p className="hero-description">나를 소개하고, 우리의 취향을 발견하고.<br />다섯 번의 작은 만남이 새로운 연결이 되는 곳.</p><div className="hero-actions"><Link className="button button--primary" href={data.preview ? '/missions/make-a-wish' : '/auth'}>{data.nickname ? '새로운 미션 만나기' : '로그인하고 시작하기'}<Icon name="arrow" size={19} /></Link><a className="text-link" href="#how-it-works">어떻게 참여하나요?<span>↗</span></a></div><div className="hero-note"><span className="mini-star">✳</span>앱 설치 없이, 가볍게 만나세요.</div></div>
      <div className="hero-art"><div className="round-sticker" aria-hidden="true"><Icon name="sparkles" size={25} /><b>FIND<br />YOUR<br />PEOPLE.</b><span>16TH EDITION</span></div><Collection data={data} /><div className="ticket-caption"><span className="little-arrow" aria-hidden="true">↳</span>흩어진 다섯 글자를 찾아, 나만의 연결을 완성해요.</div></div>
    </section>

    {!data.preview && <div className="preparation-note" role="status"><span className="status-dot" /><p>참여 기능을 준비하고 있어요. 지금은 다섯 미션을 먼저 둘러볼 수 있어요.</p><span className="small-label">COMING SOON</span></div>}
    {pending > 0 && <div className="preparation-note" role="status"><Icon name="clock" /><p>마음을 담은 {pending}개의 글을 확인하고 있어요. 기다리는 동안 다른 연결을 만나보세요.</p></div>}

    <section className="mission-section" aria-labelledby="mission-heading"><div className="section-heading"><div><p className="eyebrow">FIVE LITTLE DISCOVERIES</p><h2 id="mission-heading">나를 찾고, 우리를 잇는 <span>다섯 미션</span></h2></div><span className="section-aside">순서는 자유롭게, 발견은 새롭게.</span></div>
      <div className="mission-grid">{missions.map((mission, index) => <Link key={mission.code} href={`/missions/${mission.slug}`} className={`mission-card mission-card--${mission.color}`}><div className="mission-card-top"><span className="mission-number">MISSION 0{index + 1}</span><span className="mission-icon"><Icon name={mission.icon} size={22} /></span></div><div className="mission-card-letter" aria-hidden="true">{mission.letter}<span>✳</span></div><div className="mission-card-copy"><h3>{mission.name}</h3><b>{mission.korean}</b><p>{mission.description}</p></div><div className={`mission-card-state state-${data.progress[mission.code].toLowerCase()}`}><span><Icon name={data.progress[mission.code] === 'COMPLETED' ? 'check' : data.progress[mission.code] === 'PENDING' ? 'clock' : data.progress[mission.code] === 'UNLOCKED' ? 'sparkles' : 'lock'} size={14} />{stateLabels[data.progress[mission.code]]}</span><Icon name="arrow" size={17} /></div></Link>)}</div>
    </section>

    <section id="how-it-works" className="how-section" aria-labelledby="how-heading"><div className="section-heading"><div><p className="eyebrow">A LITTLE GUIDE</p><h2 id="how-heading">연결은 이렇게 시작돼요.</h2></div><span className="guide-doodle" aria-hidden="true">hello, you! ↘</span></div><ol className="guide-steps"><li><span>01</span><h3>태그를 찾아요</h3><p>현장의 NFC 태그나 QR을 읽고<br />숨겨진 미션을 열어보세요.</p></li><li><span>02</span><h3>나답게 참여해요</h3><p>소개, 취향, 소원, 게임, 응원.<br />작은 참여로 글자를 모아요.</p></li><li><span>03</span><h3>새로운 동료를 만나요</h3><p>나와 비슷한 취향의 동료에게<br />한 걸음 더 다가가 보세요.</p></li></ol></section>
    <section className="connection-banner"><div><span className="eyebrow">MORE THAN A NAME</span><h2>아직 모르는 사이,<br />곧 반가운 사이.</h2><p>우리의 관심사가 만나는 곳에서 새로운 동료를 발견해요.</p><Link className="text-link" href="/discovery">동료 찾기 둘러보기<Icon name="arrow" size={18} /></Link></div><div className="people-art" aria-hidden="true"><span className="person-shape person-shape--blue"><i /><b>YOU</b></span><span className="connection-spark">✳</span><span className="person-shape person-shape--coral"><i /><b>ME</b></span><span className="dashed-orbit" /></div></section>
  </div>;
}
