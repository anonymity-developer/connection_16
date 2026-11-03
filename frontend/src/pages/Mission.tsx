import { useState } from 'react';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { Button, Dialog } from '../components/ui';
import { stateLabels } from '../features/missions/model';
import type { Mission, ScreenData } from '../features/missions/model';

export function WishLake() {
  return <div className="wish-lake" aria-hidden="true"><span className="lake-star lake-star--one">✳</span><span className="lake-star lake-star--two">✧</span><div className="lake-water"><span className="lake-ripple ripple-one" /><span className="lake-ripple ripple-two" /><span className="lake-ripple ripple-three" /><span className="wish-coin coin-one">✳</span><span className="wish-coin coin-two">✳</span><span className="wish-coin coin-three">✳</span><span className="lake-caption">a little wish,<br />a little magic.</span></div><span className="lake-sprout">⌁</span></div>;
}

export function MissionPage({ mission, data }: { mission: Mission; data: ScreenData }) {
  const state = data.progress[mission.code];
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [earned, setEarned] = useState(false);
  const canPreviewWish = data.preview && state === 'UNLOCKED' && mission.code === 'MAKE_A_WISH';
  return <div className="page mission-page"><Link className="back-link" href="/">← 미션 모으기로</Link><div className="mission-detail-grid"><section className={`mission-poster mission-poster--${mission.color}`}><span className="eyebrow">A LITTLE DISCOVERY</span><span className="poster-letter" aria-hidden="true">{mission.letter}</span><span className="poster-icon"><Icon name={mission.icon} size={35} /></span><h1>{mission.name}</h1><p>{mission.korean}</p><span className="poster-footnote">TAG ME · SSAFY 16TH</span></section><section className="mission-detail" aria-labelledby="mission-title"><span className={`detail-badge state-${state.toLowerCase()}`}><Icon name={state === 'COMPLETED' ? 'check' : state === 'PENDING' ? 'clock' : state === 'UNLOCKED' ? 'sparkles' : 'lock'} size={15} />{stateLabels[state]}</span><h2 id="mission-title">{mission.korean}</h2><p className="detail-intro">{mission.description}</p>
    {mission.code === 'MAKE_A_WISH' && <WishLake />}
    {state === 'LOCKED' && <><div className="locked-note"><Icon name="tag" size={27} /><div><h3>작은 태그에서 시작되는 미션</h3><p>{mission.hint}<br />NFC를 읽거나 QR을 스캔하면 미션을 열 수 있어요.</p></div></div><Link className="button button--primary full-width" href="/auth">로그인하고 준비하기<Icon name="arrow" size={18} /></Link><p className="detail-fine">미션 주소를 여는 것만으로는 글자를 받을 수 없어요.</p></>}
    {state === 'PENDING' && <div className="pending-card" role="status"><Icon name="clock" size={30} /><h3>마음을 담은 글을 확인하고 있어요.</h3><p>확인이 끝나면 결과를 알려드릴게요.<br />기다리는 동안 다른 미션을 둘러보세요.</p><Link className="button button--secondary" href="/">다른 미션 만나기<Icon name="arrow" size={16} /></Link></div>}
    {state === 'COMPLETED' && <div className="completed-card"><span className={`earned-letter color-${mission.color}`}>{mission.letter}</span><h3>연결의 한 조각을 모았어요.</h3><p>개발용 완료 화면 미리보기예요.<br />실제 참여 기록이나 결과는 저장되지 않아요.</p><Link className="button button--secondary" href="/">수집판으로 돌아가기<Icon name="arrow" size={16} /></Link></div>}
    {canPreviewWish && <form className="wish-form" onSubmit={(event) => { event.preventDefault(); if (!draft.trim()) { setError('호수에 띄울 소원을 적어주세요.'); return; } setEarned(true); }}><label htmlFor="wish-content">지금, 어떤 소원을 품고 있나요?</label><textarea id="wish-content" placeholder="우리 모두 각자의 자리에서 반짝이기를." rows={3} maxLength={200} value={draft} aria-invalid={error ? true : undefined} aria-describedby="wish-help" onChange={(event) => { setDraft(event.target.value); setError(''); }} /><div className="wish-input-footer"><p id="wish-help">{error || '미리보기 소원은 전송되거나 저장되지 않아요.'}</p><span>{draft.length}/200</span></div><Button className="full-width" type="submit">소원 띄우기 미리보기<Icon name="sparkles" size={18} /></Button></form>}
    {state === 'UNLOCKED' && !canPreviewWish && <div className="pending-card"><Icon name={mission.icon} size={30} /><h3>이곳에서 우리의 이야기가 시작돼요.</h3><p>이 미션의 참여 화면은 다음 개발 단계에서 이어집니다.<br />지금은 해금 상태의 화면 구성만 확인할 수 있어요.</p><Link className="button button--secondary" href="/">수집판으로 돌아가기</Link></div>}
  </section></div><Dialog open={earned} title="소원 하나, 연결 한 조각." onClose={() => setEarned(false)}><div className="letter-celebration"><span>✦</span><strong>A</strong><span>✧</span></div><p>글자 획득 연출 미리보기예요.<br />실제 소원은 검수 승인 후 글자가 지급돼요.<br />이 화면에서는 소원과 완료 기록을 저장하지 않아요.</p><Button className="full-width" onClick={() => setEarned(false)}>좋아요, 확인했어요<Icon name="check" size={18} /></Button></Dialog></div>;
}
