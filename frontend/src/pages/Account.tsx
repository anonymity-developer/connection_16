import { useParticipation } from '../features/participation/context';
import { useState } from 'react';
import { Link, navigate } from '../app/navigation';
import { Icon } from '../components/Icon';
import { ParticipantPage, ParticipantSignIn } from '../components/ParticipantPage';
import { Button } from '../components/ui';
import { authApi } from '../features/auth/api';
import { useAuth } from '../features/auth/context';

export function Account({ preview }: { preview: boolean }) {
  const auth = useAuth();
  const participation = useParticipation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function logout() {
    setBusy(true); setError('');
    try { await authApi.logout(); auth.acceptUser(null); auth.setFlash('로그아웃했어요.'); navigate('/auth'); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '로그아웃하지 못했어요.'); }
    finally { setBusy(false); }
  }
  return <ParticipantPage page="account" className="account-page">
    {auth.user ? <div className="account-grid"><section className="account-panel"><h2>계정 연결을 마쳤어요</h2><dl><div><dt>Mattermost 아이디</dt><dd>{auth.user.username}</dd></div><div><dt>프로필과 미션</dt><dd>{auth.user.onboardingRequired ? '닉네임과 상세 정보 공개 안내를 확인하면 참여 준비가 끝나요.' : '프로필 등록을 마쳤어요. 현장 태그로 미션을 열어보세요.'}</dd></div></dl><div className="account-actions"><Link href="/me/profile" className="button button--primary">{auth.user.onboardingRequired ? '프로필 등록하기' : '프로필 수정하기'}<Icon name="arrow" size={17} /></Link><Link href="/me/password" className="button button--secondary">비밀번호 변경<Icon name="arrow" size={17} /></Link></div><div className="account-card-footer"><Button variant="quiet" onClick={logout} disabled={busy}>{busy ? '로그아웃 중…' : '로그아웃'}</Button></div><p role="status" className="form-notice is-error">{error}</p></section><aside className="account-record" aria-labelledby="account-record-heading"><h2 id="account-record-heading">나의 참여 기록</h2><p>모은 글자와 다음 미션은 수집판에서 확인해요.</p>{participation.collection ? <p className="account-collection-count"><strong>{participation.collection.completedCount}</strong> / 5 글자 획득</p> : <p className="account-record-status">{participation.loading ? '참여 기록을 불러오고 있어요.' : '수집판에서 참여 기록을 확인해 주세요.'}</p>}<Link className="button button--secondary" href="/">나의 수집판 보기<Icon name="arrow" size={17} /></Link></aside></div>
      : <><ParticipantSignIn title="나의 연결 기록을 만나봐요">아이디는 Mattermost와 같고, 비밀번호는 TAG ME 가입 시 설정해요.</ParticipantSignIn>{preview && <Link className="text-link account-preview-link" href="/me/password">비밀번호 변경 화면 미리보기<Icon name="arrow" size={16} /></Link>}</>}
  </ParticipantPage>;
}
