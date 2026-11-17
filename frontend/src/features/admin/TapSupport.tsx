import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, navigate } from '../../app/navigation';
import { Button, Dialog } from '../../components/ui';
import { ApiError, request } from '../auth/api';
import type { Page } from './api';
import '../../styles/support.css';

interface Attempt {
  userId: number; username: string | null; nickname: string | null; realName: string | null; region: string | null; classNo: number | null;
  attemptId: number; status: string; reason: string | null; tapCount: number | null; startsAt: string; submitDeadline: string; submittedAt: string | null;
  resetGeneration: number; retryAllowed: boolean; canRestore: boolean; recoveryReason: string;
  recordAccepted: boolean; letterEarned: boolean; rank: number | null;
  recovery: { originalStatus: string; originalReason: string | null; originalDeadline: string; effectiveDeadline: string; recognizedAt: string; restoredAt: string; actorId: number | null; operatorId: string | null; reason: string } | null;
  signals: { codes: string[]; sameTimestampPairs: number; commonIntervalMs: number | null; intervalSharePermille: number };
}
const states: Record<string, string> = { INVALID: '이전 규칙 무효', EXPIRED: '제출 기한 초과', INTERRUPTED: '중단', REVIEW_REQUIRED: '이전 검토 대기', VALID: '정상 인정', COUNTDOWN: '준비 중', PLAYING: '플레이 중', AWAITING_SUBMISSION: '제출 대기' };
const reasons: Record<string, string> = { DUPLICATE_MILLISECOND: '같은 밀리초 입력으로 무효 처리됨', SUBMISSION_EXPIRED: '기한 이후 제출', CLIENT_INTERRUPTED: '화면 이탈 또는 중단', PAGE_INTERRUPTED: '화면 이탈', INVALID_STORED_EVENTS: '저장된 이벤트 불일치' };
const signals: Record<string, string> = { SAME_TIMESTAMP: '동일 시각 입력 다수', REGULAR_INTERVALS: '일정 간격 반복', HIGH_VOLUME: '높은 입력 수' };
const date = (value: string | null) => value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul' }).format(new Date(value)) : '—';
type Pending = { key: string; user: number; body: { attemptId: number; expectedGeneration: number; reason: string; confirmation: string } };

export function TapSupport({ params, onDenied }: { params: URLSearchParams; onDenied: (message: string) => void }) {
  const selectedFilter = params.get('filter');
  const filter = selectedFilter === 'FAILED' || selectedFilter === 'RECOVERED' ? selectedFilter : 'ALL', q = params.get('q') || '';
  const parsed = Number(params.get('offset') || 0), offset = Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
  const url = `/admin/tap-attempts?${new URLSearchParams({ filter, q, offset: String(offset) })}`;
  const [data, setData] = useState<{ url: string; value: Page<Attempt> } | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  const [selection, setSelection] = useState<Attempt | null>(null), [reason, setReason] = useState(''), [confirmation, setConfirmation] = useState(''), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [blocked, setBlocked] = useState(false), [notice, setNotice] = useState('');
  const word = '재도전';
  const pending = useRef<Pending | null>(null), sending = useRef(false), deny = useRef(onDenied); deny.current = onDenied;
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    request<Page<Attempt>>(url).then(value => { if (active) setData({ url, value }); }).catch(failure => {
      if (!active) return;
      setData(null); const message = failure instanceof Error ? failure.message : '도전 목록을 불러오지 못했어요.';
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) deny.current(message); else setError(message);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [url, revision]);
  const refresh = () => setRevision(value => value + 1), page = data?.url === url ? data.value : null;
  const go = (next: number, nextFilter = filter, nextQ = q) => navigate(`/admin/rankings?${new URLSearchParams({ mission: 'FAST_TAP', filter: nextFilter, q: nextQ, offset: String(next) })}`);
  function choose(row: Attempt) { setSelection(row); setReason(''); setConfirmation(''); setError(''); setUncertain(false); setBlocked(false); pending.current = null; }
  function close() { if (busy || uncertain) return; setSelection(null); setError(''); }
  async function retry(event: FormEvent) {
    event.preventDefault(); if (!selection || sending.current || blocked) return;
    if (!reason.trim() || confirmation !== word) { setError(`사유와 확인란의 ‘${word}’를 입력해 주세요.`); return; }
    pending.current ??= { key: crypto.randomUUID(), user: selection.userId, body: { attemptId: selection.attemptId, expectedGeneration: selection.resetGeneration, reason: reason.trim(), confirmation: 'RETRY' } };
    const operation = pending.current; sending.current = true; setBusy(true); setError('');
    try {
      await request(`/admin/tap-attempts/members/${operation.user}/retry`, 'POST', operation.body, { headers: { 'Idempotency-Key': operation.key } });
      pending.current = null; setSelection(null); setUncertain(false); setNotice('F 재도전을 허용했어요. 참가자는 게임 화면을 새로고침해 주세요.'); refresh();
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : '변경 결과를 확인하지 못했어요.';
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) { onDenied(message); return; }
      setError(message); const unknown = !(failure instanceof ApiError) || failure.status >= 500;
      setUncertain(unknown); setBlocked(failure instanceof ApiError && failure.status === 409); if (!unknown) pending.current = null;
    } finally { sending.current = false; setBusy(false); }
  }
  return <section className="admin-panel tap-support">
    <div className="admin-panel-heading"><h2>FAST TAP{page ? ` · ${page.total}명` : ''}</h2><Button variant="quiet" disabled={busy || !!selection} onClick={refresh}>새로고침</Button></div>
    <p className="admin-muted">탭 수가 높은 순으로 표시해요. 이상 신호는 참고 정보이며 점수·F·순위에 영향을 주지 않아요.</p>
    <form className="tap-support-filters" onSubmit={event => { event.preventDefault(); go(0, filter, new FormData(event.currentTarget).get('q')?.toString() || ''); }}>
      <label htmlFor="tap-support-search">아이디·이름·닉네임</label><input id="tap-support-search" name="q" maxLength={64} defaultValue={q} key={q} disabled={!!selection} />
      <Button variant="secondary" type="submit" disabled={!!selection}>검색</Button>
      <Button variant={filter === 'FAILED' ? 'primary' : 'secondary'} type="button" disabled={!!selection} aria-pressed={filter === 'FAILED'} onClick={() => go(0, 'FAILED')}>실패한 도전</Button>
      <Button variant={filter === 'ALL' ? 'primary' : 'secondary'} type="button" disabled={!!selection} aria-pressed={filter === 'ALL'} onClick={() => go(0, 'ALL')}>전체 현재 도전</Button>
      <Button variant={filter === 'RECOVERED' ? 'primary' : 'secondary'} type="button" disabled={!!selection} aria-pressed={filter === 'RECOVERED'} onClick={() => go(0, 'RECOVERED')}>복구된 도전</Button>
    </form>
    {loading && <p role="status">도전 목록을 불러오고 있어요.</p>}{notice && <p role="status">{notice}</p>}{!selection && error && <p role="alert">{error}</p>}
    {page && <><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th className="num">순위</th><th>참가자</th><th className="num">탭 수</th><th>완료 시각</th><th>이상 신호·이력</th><th>처리</th></tr></thead><tbody>{page.items.map(row => <tr key={row.attemptId}>
      <td data-label="순위" className="num">{row.rank == null ? '—' : `${row.rank}위`}</td>
      <td data-label="참가자"><Link className="admin-identity" href={`/admin/members/${row.userId}`}>{row.nickname || row.realName || row.username}</Link><small>{row.username} · {row.region} {row.classNo}반</small></td>
      <td data-label="탭 수" className="num">{row.tapCount == null ? '—' : row.tapCount.toLocaleString('ko-KR')}</td>
      <td data-label="완료 시각">{date(row.submittedAt)}{!row.recordAccepted && <small>{states[row.status] || row.status} · 순위 제외</small>}</td>
      <td data-label="이상 신호·이력">{row.signals.codes.length ? row.signals.codes.map(code => <small key={code}>{signals[code] || code}</small>) : <span className="admin-muted">현재 신호 없음</span>}
        {row.recovery && <details className="tap-recovery-details"><summary>과거 반려·복구 이력</summary><dl><dt>과거 판정</dt><dd>{states[row.recovery.originalStatus] || row.recovery.originalStatus}{row.recovery.originalReason && ` · ${reasons[row.recovery.originalReason] || row.recovery.originalReason}`}</dd><dt>당시 인정 시각</dt><dd>{date(row.recovery.recognizedAt)}</dd><dt>복구 처리</dt><dd>{date(row.recovery.restoredAt)} · {row.recovery.actorId == null ? '자동 복구' : row.recovery.operatorId || `관리자 #${row.recovery.actorId}`}</dd><dt>복구 사유</dt><dd>{row.recovery.reason}</dd></dl></details>}
        {!row.recovery && row.reason && <small>과거 판정: {reasons[row.reason] || row.reason}</small>}
      </td>
      <td data-label="처리">{row.retryAllowed ? <Button variant="secondary" disabled={!!selection} onClick={() => choose(row)}>재도전 허용</Button> : row.canRestore ? <span className="admin-muted">자동 인정 대기</span> : '—'}</td>
    </tr>)}</tbody></table>{!page.items.length && <p className="admin-empty">조건에 맞는 도전이 없어요.</p>}</div>
    <div className="admin-pager"><span>{page.total.toLocaleString('ko-KR')}명</span><div><Button variant="quiet" disabled={offset === 0 || !!selection} onClick={() => go(Math.max(0, offset - 25))}>이전</Button><Button variant="quiet" disabled={page.nextOffset === null || !!selection} onClick={() => go(page.nextOffset!)}>다음</Button></div></div></>}
    <Dialog open={!!selection} title="FAST TAP 재도전 허용" onClose={close}>
      <form className="admin-reset-form" onSubmit={retry} aria-busy={busy} noValidate>
        <p><strong>{selection?.nickname || selection?.username}</strong> · F 도전 #{selection?.attemptId}</p><p>실패한 F 기록을 보관하고 다시 도전하게 합니다. 기존 태그 해금·프로필·다른 게임은 유지합니다.</p>
        <label htmlFor="tap-retry-reason">{word} 사유</label><textarea id="tap-retry-reason" maxLength={300} rows={3} value={reason} disabled={busy || uncertain || blocked} onChange={event => setReason(event.target.value)} />
        <label htmlFor="tap-retry-confirm">확인란에 ‘{word}’을 입력해 주세요</label><input id="tap-retry-confirm" value={confirmation} disabled={busy || uncertain || blocked} onChange={event => setConfirmation(event.target.value)} autoComplete="off" />
        {error && <p role="alert">{error}</p>}{uncertain && <p>응답을 확인하지 못했어요. 대상과 사유를 유지한 같은 요청으로 결과를 확인해 주세요.</p>}
        {blocked ? <Button type="button" onClick={() => { setSelection(null); setError(''); refresh(); }}>목록 다시 확인</Button> : <Button type="submit" disabled={busy}>{busy ? '처리 중…' : uncertain ? '같은 요청 다시 확인' : '재도전 허용하기'}</Button>}
        <Button type="button" variant="quiet" disabled={busy || uncertain} onClick={close}>취소</Button>
      </form>
    </Dialog>
  </section>;
}
