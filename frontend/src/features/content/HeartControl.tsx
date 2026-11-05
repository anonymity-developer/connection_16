import { useEffect, useRef, useState } from 'react';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { ApiError } from '../auth/api';
import { reactionsApi } from './reactions-api';
import type { LikeState } from './reactions-api';
export function HeartControl({ id, initial, loading }: { id: number; initial: LikeState | null; loading: boolean }) {
  const [state, setState] = useState<LikeState | null>(initial); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [blocked, setBlocked] = useState(false);
  const active = useRef(true); const desired = useRef<boolean | null>(null);
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  useEffect(() => { setState(initial); desired.current = null; setBlocked(false); setError(''); }, [initial]);
  async function refresh() {
    if (busy) return; setBusy(true); setError('');
    try { const next = await reactionsApi.heart(id); if (active.current) { setState(next); setBlocked(false); desired.current = null; } }
    catch (failure) { if (active.current) { setState(null); setError(failure instanceof Error ? failure.message : '하트 상태를 확인하지 못했어요.'); } }
    finally { if (active.current) setBusy(false); }
  }
  async function update() {
    if (busy || !state || blocked) return; if (desired.current === null) desired.current = !state.liked;
    setBusy(true); setError('');
    try { const next = await reactionsApi.setHeart(id, desired.current); if (active.current) { setState(next); desired.current = null; } }
    catch (failure) { if (active.current) { setError(failure instanceof Error ? failure.message : '하트 응답을 확인하지 못했어요.'); if (failure instanceof ApiError && failure.status < 500) { desired.current = null; setBlocked(true); if (failure.status === 404) setState(null); } } }
    finally { if (active.current) setBusy(false); }
  }
  return <div className="heart-control" role="group" aria-label="이 응원 카드의 하트">
    {state ? <><Button type="button" variant="quiet" aria-pressed={state.liked} disabled={busy || loading || !state.canLike || blocked} onClick={update}><Icon name="heart" size={18} />{busy ? '확인 중…' : desired.current !== null ? '같은 하트 요청 다시 보내기' : state.liked ? '하트 취소하기' : '하트 남기기'}</Button><span className="heart-count" aria-live="polite">하트 {state.count}개</span>{!state.canLike && <p className="result-footnote">지금은 이 카드에 하트를 남기거나 취소할 수 없어요.</p>}</> : <Button type="button" variant="quiet" disabled={busy || loading} onClick={refresh}>{loading ? '하트 불러오는 중…' : '하트 상태 확인하기'}</Button>}
    {error && <><p className="form-notice is-error" role="alert">{error}</p><Button type="button" variant="quiet" disabled={busy} onClick={refresh}>서버의 하트 상태 확인하기</Button></>}
  </div>;
}
