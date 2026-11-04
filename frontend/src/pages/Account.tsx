import { participationApi } from '../features/participation/api';
import { useState } from 'react';
import { Link, navigate } from '../app/navigation';
import { Icon } from '../components/Icon';
import { Button, EmptyState } from '../components/ui';
import { authApi } from '../features/auth/api';
import { useAuth } from '../features/auth/context';

export function Account({ preview }: { preview: boolean }) {
  const auth = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function resume() {
    setError('');
    try { if ((await participationApi.pending()).present) navigate('/t/resume'); else auth.setFlash('이어갈 태그가 없어요. 현장 태그를 읽어주세요.'); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '읽어 둔 태그를 확인하지 못했어요.'); }
  }
  async function logout() {
    setBusy(true); setError('');
    try { await authApi.logout(); auth.acceptUser(null); auth.setFlash('로그아웃했어요.'); navigate('/auth'); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '로그아웃하지 못했어요.'); }
    finally { setBusy(false); }
  }
  return <div className="page secondary-page"><div className="page-heading"><p className="context-label">TAG ME 계정</p><h1>마이페이지</h1><p>나의 계정과 참여 준비 상태를 확인해요.</p></div>
    {auth.user ? <section className="account-panel"><h2>계정 연결을 마쳤어요</h2><dl><div><dt>Mattermost 아이디</dt><dd>{auth.user.username}</dd></div><div><dt>프로필과 미션</dt><dd>{auth.user.onboardingRequired ? '프로필과 공개 동의를 확인하면 참여 준비가 끝나요.' : '프로필 등록을 마쳤어요. 현장 태그로 미션을 열어보세요.'}</dd></div></dl><Link href="/me/profile" className="button button--primary">{auth.user.onboardingRequired ? '프로필 등록하기' : '프로필 수정하기'}<Icon name="arrow" size={17} /></Link><Button variant="quiet" onClick={resume}>읽어 둔 태그 이어가기</Button><Link href="/me/password" className="button button--secondary">비밀번호 변경<Icon name="arrow" size={17} /></Link><Button variant="quiet" onClick={logout} disabled={busy}>{busy ? '로그아웃 중…' : '로그아웃'}</Button><p role="status" className="form-notice is-error">{error}</p></section>
      : <><EmptyState title="로그인 후 내 기록을 확인해요" action={<Link href="/auth" className="button button--primary">로그인 화면으로<Icon name="arrow" size={17} /></Link>}>아이디는 Mattermost와 같고, 비밀번호는 TAG ME 가입 시 설정해요.</EmptyState>{preview && <Link className="text-link account-preview-link" href="/me/password">비밀번호 변경 화면 미리보기<Icon name="arrow" size={16} /></Link>}</>}
  </div>;
}
