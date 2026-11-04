import { useRef, useState } from 'react';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { Button, Dialog } from '../components/ui';
import { StateBadge } from './Home';
import type { Mission, ScreenData } from '../features/missions/model';

export function WishLake() {
  return <figure className="wish-lake"><svg viewBox="0 0 440 150" role="img" aria-label="소원이 담길 호수를 표현한 그림"><ellipse cx="220" cy="108" rx="152" ry="29" /><ellipse cx="220" cy="106" rx="104" ry="17" /><ellipse cx="220" cy="103" rx="56" ry="8" /><circle cx="220" cy="66" r="28" /><path d="m210 76 10-23 10 23m-17-7h14" /></svg><figcaption>소원은 검수 승인 후 호수에 공개돼요.</figcaption></figure>;
}

export function MissionPage({ mission, data }: { mission: Mission; data: ScreenData }) {
  const auth = useAuth();
  const state = data.progress[mission.code];
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [earned, setEarned] = useState(false);
  const input = useRef<HTMLTextAreaElement>(null);
  const canPreviewWish = data.preview && state === 'UNLOCKED' && mission.code === 'MAKE_A_WISH';
  return <div className="page mission-page"><Link className="back-link" href="/"><Icon name="arrowLeft" size={16} />미션 모으기</Link>
    <header className="mission-detail-heading"><span className={`mission-letter ${state === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true">{mission.letter}</span><div><p className="context-label">{mission.name}</p><h1>{mission.korean}</h1></div><StateBadge state={state} /></header>
    <div className="mission-detail-grid"><section className="mission-detail" aria-label={`${mission.name} 참여 화면`}><p className="detail-intro">{mission.description}</p>
      {mission.code === 'MAKE_A_WISH' && <WishLake />}
      {state === 'LOCKED' && <div className="mission-message"><Icon name="tag" size={28} /><h2>현장에서 태그를 찾아주세요</h2><p>{mission.hint}<br />NFC를 읽거나 QR을 스캔하면 미션으로 이어져요.</p><Link className="button button--primary" href={data.preview ? '/' : auth.user ? '/me' : '/auth'}>{data.preview ? '다른 미션 둘러보기' : auth.user ? '내 계정 확인하기' : '로그인 화면으로'}<Icon name="arrow" size={18} /></Link></div>}
      {state === 'PENDING' && <div className="mission-message" role="status"><Icon name="clock" size={28} /><h2>마음을 담은 글을 확인하고 있어요</h2><p>검수 승인 후 글자가 지급돼요.<br />기다리는 동안 다른 미션을 둘러보세요.</p><Link className="button button--secondary" href="/">다른 미션 보기<Icon name="arrow" size={16} /></Link></div>}
      {state === 'COMPLETED' && <div className="mission-message"><span className="earned-letter" aria-label={`획득 글자 ${mission.letter}`}>{mission.letter}</span><h2>연결의 한 조각을 모았어요</h2><p>개발용 완료 화면 미리보기예요.<br />실제 참여 기록이나 결과는 저장되지 않아요.</p><Link className="button button--secondary" href="/">수집판으로 돌아가기<Icon name="arrow" size={16} /></Link></div>}
      {canPreviewWish && <form className="wish-form" onSubmit={(event) => { event.preventDefault(); if (!draft.trim()) { setError('호수에 띄울 소원을 적어주세요.'); input.current?.focus(); return; } setEarned(true); }}><label htmlFor="wish-content">지금, 어떤 소원을 품고 있나요?</label><textarea ref={input} id="wish-content" placeholder="함께 이루고 싶은 작은 바람을 적어주세요." rows={4} maxLength={200} value={draft} aria-invalid={error ? true : undefined} aria-describedby="wish-help wish-count" onChange={(event) => { setDraft(event.target.value); setError(''); }} /><div className="wish-input-footer"><p id="wish-help" className={error ? 'field-help--error' : ''} role={error ? 'alert' : undefined}>{error || '미리보기 소원은 전송되거나 저장되지 않아요.'}</p><span id="wish-count">{draft.length}/200</span></div><Button className="full-width" type="submit">소원 띄우기 미리보기<Icon name="arrow" size={18} /></Button></form>}
      {state === 'UNLOCKED' && !canPreviewWish && <div className="mission-message"><Icon name={mission.icon} size={28} /><h2>참여 화면을 준비하고 있어요</h2><p>지금은 해금 상태의 화면 구성만 확인할 수 있어요.<br />이 미션의 실제 참여 기능은 다음 단계에서 연결돼요.</p><Link className="button button--secondary" href="/">수집판으로 돌아가기</Link></div>}
    </section><aside className="mission-guide" aria-labelledby="mission-guide-heading"><h2 id="mission-guide-heading">이 미션에서 모을 글자</h2><div className="mission-reward"><span>{mission.letter}</span><div><b>{mission.name}</b><p>{mission.code === 'MAKE_A_WISH' || mission.code === 'SAY_SOMETHING' ? '검수 승인 후 획득' : '미션 완료 후 획득'}</p></div></div><ul><li>미션 주소를 여는 것만으로는 해금되거나 글자가 지급되지 않아요.</li><li>{mission.code === 'MAKE_A_WISH' || mission.code === 'SAY_SOMETHING' ? '제출과 승인 상태를 구분해 안내해요.' : '현장 태그를 통해 미션에 참여해 주세요.'}</li><li>{data.preview ? '개발용 상태 미리보기예요. 실제 기록은 생성되지 않아요.' : '현재는 참여 기능을 준비하고 있어요.'}</li></ul><Link className="text-link" href="/">전체 미션 보기<Icon name="arrow" size={16} /></Link></aside></div>
    <Dialog open={earned} title="소원을 띄운 뒤의 화면이에요" onClose={() => setEarned(false)}><div className="letter-celebration"><strong>A</strong></div><p>글자 획득 연출 미리보기예요. 실제 소원은 검수 승인 후 글자가 지급돼요. 이 화면에서는 소원과 완료 기록을 저장하지 않아요.</p><Button className="full-width" onClick={() => setEarned(false)}>확인했어요<Icon name="check" size={18} /></Button></Dialog>
  </div>;
}
