import { WishLake } from '../features/content/WishLake';
import '../features/content/wish-lake.css';
import { MissionProgress } from '../components/MissionProgress';
import { LetterSticker } from '../components/LetterSticker';
import { ContentMission } from '../features/content/ContentMission';
import { SyncMission } from '../features/results/SyncMission';
import { TapMission } from '../features/results/TapMission';
import { useRef, useState } from 'react';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { Button, Dialog } from '../components/ui';
import { StateBadge } from './Home';
import type { Mission, ScreenData } from '../features/missions/model';

export function MissionPage({ mission, data }: { mission: Mission; data: ScreenData }) {
  const auth = useAuth();
  const state = data.progress[mission.code];
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [earned, setEarned] = useState(false);
  const input = useRef<HTMLTextAreaElement>(null);
  const resultMission = data.serverLoaded && state !== 'LOCKED' && (mission.code === 'SYNC_ME' || mission.code === 'FAST_TAP');
  const contentMission = data.serverLoaded && state !== 'LOCKED' && (mission.code === 'MAKE_A_WISH' || mission.code === 'SAY_SOMETHING');
  const canPreviewWish = data.preview && state === 'UNLOCKED' && mission.code === 'MAKE_A_WISH';
  return <div className="page mission-page participant-page" data-mission={mission.code}><Link className="back-link" href="/"><Icon name="arrowLeft" size={16} />미션 모으기</Link>
    <header className="mission-detail-heading"><span className={`mission-letter ${state === 'COMPLETED' ? 'is-collected' : ''}`} aria-hidden="true"><LetterSticker code={mission.code} collectionState={state === 'COMPLETED' ? 'collected' : undefined} celebrate={!!data.serverLoaded && !data.preview} /></span><div><p className="context-label">{mission.name}</p><h1>{mission.name}</h1></div>{(data.preview || data.serverLoaded) && <StateBadge state={state} missionCode={mission.code} />}</header>
    <div className="mission-detail-grid"><section className="mission-detail" aria-label={`${mission.name} 참여 화면`}><p className="detail-intro">{mission.description}</p><p className="mission-encouragement">{mission.encouragement}</p>
      {mission.code === 'MAKE_A_WISH' && !contentMission && <WishLake />}
      {state === 'LOCKED' && mission.code !== 'SHOW_ME' && <div className="mission-access-note"><Icon name="tag" size={20} /><p>현장 NFC·QR로 참여해요. 상세 페이지를 여는 것만으로는 해금되지 않아요.</p>{!auth.user && !data.preview && <Link className="text-link" href="/auth">로그인하기<Icon name="arrow" size={16} /></Link>}</div>}
      {state === 'PENDING' && !contentMission && !resultMission && <div className="mission-message" role="status"><Icon name="clock" size={28} /><h2>참여 중인 미션이에요</h2><p>진행 상태를 확인하고 미션을 이어가요. 글자 지급 여부는 서버 기록을 따라요.</p><Link className="button button--secondary" href="/">다른 미션 보기<Icon name="arrow" size={16} /></Link></div>}
      {state === 'COMPLETED' && !resultMission && !contentMission && <div className="mission-message"><span className="earned-letter" aria-label={`획득 글자 ${mission.letter}`}><LetterSticker code={mission.code} collectionState="collected" celebrate={!!data.serverLoaded && !data.preview} /></span><h2>연결의 한 조각을 모았어요</h2><p>{data.preview ? '개발용 완료 화면 미리보기예요. 실제 기록은 저장되지 않아요.' : '서버에 저장된 최초 완료 기록이에요. 다시 접속해도 글자는 유지돼요.'}</p><Link className="button button--secondary" href="/">수집판으로 돌아가기<Icon name="arrow" size={16} /></Link></div>}
      {canPreviewWish && <form className="wish-form" onSubmit={(event) => { event.preventDefault(); if (!draft.trim()) { setError('호수에 띄울 소원을 적어주세요.'); input.current?.focus(); return; } setEarned(true); }}><label htmlFor="wish-content">지금, 어떤 소원을 품고 있나요?</label><textarea ref={input} id="wish-content" placeholder="함께 이루고 싶은 작은 바람을 적어주세요." rows={4} maxLength={200} value={draft} aria-invalid={error ? true : undefined} aria-describedby="wish-help wish-count" onChange={(event) => { setDraft(event.target.value); setError(''); }} /><div className="wish-input-footer"><p id="wish-help" className={error ? 'field-help--error' : ''} role={error ? 'alert' : undefined}>{error || '미리보기 소원은 전송되거나 저장되지 않아요.'}</p><span id="wish-count">{draft.length}/200</span></div><Button className="full-width" type="submit">소원 띄우기 미리보기<Icon name="arrow" size={18} /></Button></form>}
      {state !== 'COMPLETED' && mission.code === 'SHOW_ME' && <div className="mission-message"><Icon name="person" size={28} /><h2>첫인사를 완성해 주세요</h2><p>프로필을 완성하면 첫 번째 S를 받아요! 한 줄 소개와 관심사 1개 이상, 참여 안내와 공개 동의를 확인해 주세요. 사전 등록했다면 행사 시작 후 접속할 때 지급을 확인해요.</p><Link href="/me/profile" className="button button--primary">프로필 확인하고 저장하기<Icon name="arrow" size={18} /></Link></div>}
      {resultMission && mission.code === 'SYNC_ME' && <SyncMission />}
      {resultMission && mission.code === 'FAST_TAP' && <TapMission />}
      {contentMission && <ContentMission kind={mission.code === 'MAKE_A_WISH' ? 'wishes' : 'cheers'} />}
      {state === 'UNLOCKED' && !resultMission && !contentMission && !canPreviewWish && mission.code !== 'SHOW_ME' && <div className="mission-message"><Icon name={mission.icon} size={28} /><h2>참여 화면을 준비하고 있어요</h2><p>지금은 해금 상태의 화면 구성만 확인할 수 있어요.<br />이 미션의 실제 참여 기능은 다음 단계에서 연결돼요.</p><Link className="button button--secondary" href="/">수집판으로 돌아가기</Link></div>}
    </section><MissionProgress mission={mission} data={data} signedIn={!!auth.user} /></div>
    <Dialog open={earned} title="소원을 띄운 뒤의 화면이에요" onClose={() => setEarned(false)}><div className="letter-celebration"><LetterSticker code="MAKE_A_WISH" /></div><p>글자 획득 연출 미리보기예요. 실제 소원은 검수 승인 후 글자가 지급돼요. 이 화면에서는 소원과 완료 기록을 저장하지 않아요.</p><Button className="full-width" onClick={() => setEarned(false)}>확인했어요<Icon name="check" size={18} /></Button></Dialog>
  </div>;
}
