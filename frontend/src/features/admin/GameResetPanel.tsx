import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Button, Dialog } from '../../components/ui';
import { ApiError, request } from '../auth/api';
import type { MemberDetail, ResetRecord } from './api';

export function GameResetPanel({ detail, onReset, onDenied }: { detail: MemberDetail; onReset: () => void; onDenied: (message: string) => void }) {
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [reason, setReason] = useState('개발 테스트 재참여'), [confirmation, setConfirmation] = useState(''), [notice, setNotice] = useState('');
  const [uncertain, setUncertain] = useState(false), [blocked, setBlocked] = useState(false);
  const pending = useRef<{ key: string; body: { expectedGeneration: number; reason: string; confirmation: string } } | null>(null);
  const reasonInput = useRef<HTMLTextAreaElement>(null), confirmationInput = useRef<HTMLInputElement>(null), submitting = useRef(false);
  const participated = detail.member.status === 'ACTIVE' && detail.missions.some(m => m.code !== 'SHOW_ME' && (m.participated || m.state === 'COMPLETED' || m.state === 'IN_PROGRESS'));
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting.current || blocked) return;
    if (!reason.trim()) { setError('초기화 사유를 입력해 주세요.'); reasonInput.current?.focus(); return; }
    if (confirmation !== '초기화') { setError('확인란에 초기화를 입력해 주세요.'); confirmationInput.current?.focus(); return; }
    pending.current ??= { key: crypto.randomUUID(), body: { expectedGeneration: detail.resetGeneration ?? 0, reason: reason.trim(), confirmation: 'RESET' } };
    submitting.current = true; setBusy(true); setError('');
    try {
      await request<ResetRecord>(`/admin/members/${detail.member.id}/reset-games`, 'POST', pending.current.body, { headers: { 'Idempotency-Key': pending.current.key } });
      pending.current = null; setUncertain(false); setOpen(false); setConfirmation('');
      setNotice('게임 참여를 초기화했어요. 계정·프로필·첫 S와 태그 해금은 유지돼요. 참가자는 화면을 새로고침하면 바로 재참여할 수 있어요.');
      onReset();
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : '초기화 결과를 확인하지 못했어요.';
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) { onDenied(message); return; }
      setError(message);
      const unknown = !(failure instanceof ApiError) || failure.status >= 500;
      setUncertain(unknown); setBlocked(!unknown && failure instanceof ApiError && failure.status === 409);
      if (!unknown) pending.current = null;
    } finally { submitting.current = false; setBusy(false); }
  }
  function begin() { pending.current = null; setOpen(true); setReason('개발 테스트 재참여'); setConfirmation(''); setError(''); setBlocked(false); setUncertain(false); }
  return <section className="admin-panel admin-reset-panel">
    <div className="admin-panel-heading"><h2>게임 재참여</h2><Button variant="secondary" disabled={!participated || busy} onClick={begin}>게임 참여 초기화</Button></div>
    <p>성향·탭·소원·응원과 게임 반응을 초기화하고 현재 순위·전체 완료 순서에서 제외합니다.</p>
    <p className="admin-muted">계정·프로필·첫 S·태그 해금·접속 이력·동료 관계는 유지합니다. 이전 글과 결과는 보관하며 초기화 이력을 남깁니다.</p>
    {!participated && <p className="admin-muted">현재 초기화할 게임 참여 기록이 없어요.</p>}
    {notice && <p role="status">{notice}</p>}
    {!!detail.resets?.length && <details><summary>계정·참여 조정 이력 · 최근 {detail.resets.length}건</summary><ol className="admin-reset-history">{detail.resets.map(row => <li key={row.id}><strong>{row.generation}번째 조정 · {row.missionCode ? `${row.missionCode} · ` : ''}{({RESET_GAMES:'네 게임 초기화',UNLOCK:'해금',LOCK:'다시 잠금',GRANT:'획득 부여',REVOKE:'획득 취소',DEACTIVATE:'계정 비활성화'} as Record<string,string>)[row.operation||'RESET_GAMES']}</strong><span>{new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Seoul' }).format(new Date(row.createdAt))} · 운영자 #{row.actorId}</span><p>{row.reason}</p></li>)}</ol></details>}
    <Dialog open={open} title="게임 참여 초기화" onClose={() => { if (!busy) setOpen(false); }}>
      <form className="admin-reset-form" onSubmit={submit} noValidate aria-busy={busy}>
        <p><strong>{detail.member.nickname || detail.member.realName || detail.member.username}</strong> · {detail.member.username}</p>
        <p>네 게임의 현재 참여와 순위를 초기화합니다. 이전 기록은 보관하며, 이미 해금한 게임은 다시 태그하지 않고 재참여할 수 있습니다.</p>
        <label htmlFor="reset-reason">초기화 사유</label><textarea id="reset-reason" ref={reasonInput} rows={3} maxLength={300} value={reason} onChange={event => setReason(event.target.value)} disabled={busy || uncertain || blocked} />
        <label htmlFor="reset-confirmation">확인란에 ‘초기화’를 입력해 주세요</label><input id="reset-confirmation" ref={confirmationInput} autoComplete="off" value={confirmation} onChange={event => setConfirmation(event.target.value)} disabled={busy || uncertain || blocked} />
        {error && <p role="alert">{error}</p>}
        {uncertain && <p>응답을 확인하지 못했어요. 같은 요청을 다시 보내도 초기화는 한 번만 실행됩니다.</p>}
        {blocked ? <Button type="button" disabled={busy} onClick={() => { setOpen(false); onReset(); }}>참여 상태 다시 불러오기</Button> : <Button type="submit" disabled={busy}>{busy ? '초기화 중…' : uncertain ? '같은 초기화 다시 요청' : '게임 참여 초기화 실행'}</Button>}
        <Button type="button" variant="quiet" disabled={busy} onClick={() => setOpen(false)}>취소</Button>
      </form>
    </Dialog>
  </section>;
}
