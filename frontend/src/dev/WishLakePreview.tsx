import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WishGallery } from '../features/content/WishGallery';
import type { PublicContent } from '../features/content/api';
import '../styles/global.css';
import '../styles/participant.css';
import '../styles/friendly.css';
import '../styles/polish.css';

// Separate Vite development entry; no authentication, API requests, storage or real identities.
const examples = ['꾸준히 공부하며 나만의 속도를 찾고 싶어요.', '새로운 동료와 편하게 이야기할 수 있기를!', '서로 응원하며 이번 프로젝트를 끝까지 완성하고 싶어요.', '바쁜 하루에도 작은 즐거움을 놓치지 않기를.', '내일의 나에게 한 걸음 가까워지길.', '마음 맞는 팀원들과 좋은 추억을 만들고 싶어요.', '좋아하는 일을 오래 즐길 수 있기를.', '용기 내어 첫인사를 건네고 싶어요.'];
const wishes: PublicContent[] = examples.map((content, index) => ({ id: index + 1, content, templateId: 'PLAIN', style: 'ROSE', anonymous: true, nickname: null, profileId: null, luckScore: null, landingZone: null }));
function Preview() {
  const [count, setCount] = useState(8); const [receipt, setReceipt] = useState<number | null>(null);
  return <main className="participant-shell page" style={{ maxWidth: 760, margin: '0 auto', padding: 20 }}>
    <p className="form-notice">개발용 가상 소원 · 실제 서버 저장·검수·글자 지급 없음</p><p className="context-label">TAG ME · A WISH COME TRUE</p><h1>소원 호수 비교</h1>
    <p>두 보기 버튼으로 같은 가상 소원을 비교해 주세요. 댓글·전송 API는 호출하지 않아요.</p>
    <div className="wish-view-switch"><button className="button button--secondary" onClick={() => { setCount(count ? 0 : 8); setReceipt(null); }}>{count ? '빈 호수 보기' : '가상 소원 보기'}</button><button className="button button--secondary" onClick={() => setReceipt((receipt || 0) + 1)}>접수 연출 미리보기</button></div>
    <WishGallery items={wishes.slice(0, count)} receipt={receipt} renderComments={() => <p className="result-footnote">개발 미리보기에서는 댓글을 전송하지 않아요.</p>} />
  </main>;
}
if (import.meta.env.DEV) createRoot(document.getElementById('root')!).render(<Preview />);
