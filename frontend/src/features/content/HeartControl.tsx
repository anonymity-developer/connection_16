import { useEffect, useRef, useState } from 'react';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { ApiError } from '../auth/api';
import { reactionsApi as serverApi } from './reactions-api';
import type { LikeState } from './reactions-api';

export function HeartControl({ id, initial, loading, api: reactionsApi = serverApi, onChange, onPendingChange }: { id: number; initial: LikeState | null; loading: boolean; api?: typeof serverApi; onChange?: (state: LikeState) => void; onPendingChange?: (pending: boolean) => void }) {
  const [state, setState] = useState<LikeState | null>(initial), [busy, setBusy] = useState(false), [error, setError] = useState(''), [blocked, setBlocked] = useState(false);
  const active = useRef(true), inFlight = useRef(false);
  useEffect(() => { active.current = true; return () => { active.current = false; if (inFlight.current) onPendingChange?.(false); }; }, []);
  useEffect(() => { if (!inFlight.current) setState(initial); }, [initial]);
  function accept(next: LikeState) { setState(next); onChange?.(next); }
  async function refresh() {
    if (inFlight.current) return;
    inFlight.current = true; onPendingChange?.(true); setBusy(true); setError('');
    try { const next = await reactionsApi.heart(id); if (active.current) { accept(next); setBlocked(false); } }
    catch (failure) { if (active.current) { setState(null); setError(failure instanceof Error ? failure.message : '하트 상태를 확인하지 못했어요.'); } }
    finally { inFlight.current = false; if (active.current) { setBusy(false); onPendingChange?.(false); } }
  }
  async function update() {
    if (inFlight.current || !state || !state.canLike || loading || blocked) return;
    inFlight.current = true; onPendingChange?.(true);
    const previous = state, desired = !previous.liked;
    setState({ ...previous, liked: desired, count: Math.max(0, previous.count + (desired ? 1 : -1)) });
    setBusy(true); setError('');
    try { const next = await reactionsApi.setHeart(id, desired); if (active.current) { accept(next); setBlocked(false); } }
    catch (failure) {
      if (!active.current) return;
      // A failed response may follow a committed PUT/DELETE. Re-read instead of inverting twice.
      setState(previous);
      const denied = failure instanceof ApiError && [401, 403, 404, 409].includes(failure.status);
      try {
        const actual = await reactionsApi.heart(id);
        if (active.current) { accept(actual); setBlocked(denied); if (denied || actual.liked !== desired) setError(failure instanceof Error ? failure.message : '하트를 변경하지 못했어요.'); }
      } catch { if (active.current) { setState(null); setBlocked(true); setError('하트 상태를 확인하지 못했어요. 다시 확인해 주세요.'); } }
    } finally { inFlight.current = false; if (active.current) { setBusy(false); onPendingChange?.(false); } }
  }
  return <div className="heart-control" role="group" aria-label="이 응원 카드의 하트" aria-busy={busy}>
    {state ? <Button className="heart-toggle" type="button" variant="quiet" aria-label={state.liked ? '하트 취소하기' : '하트 남기기'} aria-pressed={state.liked} title={!state.canLike ? '내 카드이거나 현재 하트를 변경할 수 없는 카드예요.' : undefined} disabled={busy || loading || !state.canLike || blocked} onClick={update}><Icon name="heart" size={22} /><span className="heart-count" aria-live="polite">{state.count}</span></Button>
      : <Button className="heart-toggle" type="button" variant="quiet" aria-label="하트 상태 확인하기" disabled={busy || loading} onClick={refresh}><Icon name="heart" size={22} /><span aria-hidden="true">—</span></Button>}
    {error && <><p className="form-notice is-error" role="alert">{error}</p><Button type="button" variant="quiet" disabled={busy} onClick={refresh}>서버의 하트 상태 확인하기</Button></>}
  </div>;
}
