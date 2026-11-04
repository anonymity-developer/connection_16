import { useState } from 'react';
import { Link, navigate } from '../app/navigation';
import { Button, EmptyState } from '../components/ui';
import { Icon } from '../components/Icon';
import { useAuth } from '../features/auth/context';
import { participationApi } from '../features/participation/api';
import { useParticipation } from '../features/participation/context';
import { missions } from '../features/missions/model';

export function TagPage({ token }: { token: string }) {
  const auth = useAuth(); const participation = useParticipation();
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const valid = token === 'resume' || /^[A-Za-z0-9_-]{43}$/.test(token);
  async function open() {
    if (busy || !valid) return;
    setBusy(true); setError('');
    try {
      if (!auth.user) {
        if (token !== 'resume') await participationApi.rememberTag(token);
        auth.rememberReturnPath(`/t/${token}`); navigate('/auth'); return;
      }
      const result = token === 'resume' ? await participationApi.resume() : await participationApi.resolve(token);
      const mission = missions.find(m => m.code === result.missionCode);
      if (!mission || result.route !== `/missions/${mission.slug}`) throw new Error('이동할 미션을 확인하지 못했어요.');
      await participation.refresh();
      auth.setFlash(result.newlyCompleted ? 'SHOW ME의 첫 번째 S를 모았어요.' : result.newlyUnlocked ? `${mission.name} 미션을 열었어요.` : '이미 열어 둔 미션이에요. 저장된 상태로 이어갈게요.');
      navigate(result.route);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '태그를 확인하지 못했어요.'); }
    finally { setBusy(false); }
  }
  return <div className="page secondary-page tag-page"><div className="page-heading"><p className="context-label">NFC · QR</p><h1>현장 태그 확인</h1><p>현장에서 찾은 태그로 미션을 열어요.</p></div>
    <EmptyState title={!valid ? '태그 주소를 다시 확인해 주세요' : auth.user ? '다음 미션을 열어볼까요?' : '로그인하고 태그로 이어가요'} action={valid ? <Button onClick={open} disabled={busy || !auth.ready}>{busy ? '태그 확인 중…' : auth.user ? '태그로 미션 열기' : '로그인하고 이어가기'}<Icon name="arrow" size={18} /></Button> : <Link href="/" className="button button--secondary">미션 안내로 돌아가기</Link>}>{valid ? '태그와 행사 상태는 서버에서 확인해요. 태그를 다시 읽어도 기존 해금과 글자 기록은 유지돼요.' : 'NFC를 다시 읽거나 현장의 QR 코드를 확인해 주세요.'}</EmptyState>
    {error && <p className="form-notice is-error tag-error" role="alert">{error}</p>}<p className="tag-footnote">로그인 전 연결은 같은 브라우저에서 15분 동안 이어갈 수 있어요. 만료되면 현장 태그를 다시 읽어주세요.</p><Link href="/" className="text-link">전체 미션 보기<Icon name="arrow" size={16} /></Link>
  </div>;
}
