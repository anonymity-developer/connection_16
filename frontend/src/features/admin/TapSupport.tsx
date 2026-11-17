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
  signals: { codes: string[]; sameTimestampPairs: number; commonIntervalMs: number | null; intervalSharePermille: number };
}
const states: Record<string, string> = { INVALID: '이전 규칙 무효', EXPIRED: '제출 기한 초과', INTERRUPTED: '중단', REVIEW_REQUIRED: '이전 검토 대기', VALID: '정상 완료', COUNTDOWN: '준비 중', PLAYING: '플레이 중', AWAITING_SUBMISSION: '제출 대기' };
const reasons: Record<string, string> = { DUPLICATE_MILLISECOND: '같은 밀리초 입력으로 무효 처리됨', SUBMISSION_EXPIRED: '기한 이후 제출', CLIENT_INTERRUPTED: '화면 이탈 또는 중단', PAGE_INTERRUPTED: '화면 이탈', INVALID_STORED_EVENTS: '저장된 이벤트 불일치' };
const signals: Record<string, string> = { SAME_TIMESTAMP: '동일 시각 입력 다수', REGULAR_INTERVALS: '일정 간격 반복', HIGH_VOLUME: '높은 입력 수' };
const date = (value: string | null) => value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul' }).format(new Date(value)) : '—';
type Pending = { key: string; user: number; action: 'RESTORE' | 'RETRY'; body: { attemptId: number; expectedGeneration: number; reason: string; confirmation: string } };

export function TapSupport({ params, onDenied }: { params: URLSearchParams; onDenied: (message: string) => void }) {
  const filter = params.get('filter') === 'ALL' ? 'ALL' : 'FAILED', q = params.get('q') || '';
  const parsed = Number(params.get('offset') || 0), offset = Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
  const url = `/admin/tap-attempts?${new URLSearchParams({ filter, q, offset: String(offset) })}`;
  const [data, setData] = useState<{ url: string; value: Page<Attempt> } | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  const [selection, setSelection] = useState<Attempt | null>(null), [reason, setReason] = useState(''), [confirmation, setConfirmation] = useState(''), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [blocked, setBlocked] = useState(false), [notice, setNotice] = useState('');
  const [action, setAction] = useState<'RESTORE' | 'RETRY'>('RETRY');
  const word = action === 'RESTORE' ? '복구' : '재도전';
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
  const go = (next: number, nextFilter = filter, nextQ = q) => navigate(`/admin/tap-support?${new URLSearchParams({ filter: nextFilter, q: nextQ, offset: String(next) })}`);
  function choose(row: Attempt, operation: 'RESTORE' | 'RETRY') { setAction(operation); setSelection(row); setReason(''); setConfirmation(''); setError(''); setUncertain(false); setBlocked(false); pending.current = null; }
  function close() { if (busy || uncertain) return; setSelection(null); setError(''); }
  async function retry(event: FormEvent) {
    event.preventDefault(); if (!selection || sending.current || blocked) return;
    if (!reason.trim() || confirmation !== word) { setError(`사유와 확인란의 ‘${word}’를 입력해 주세요.`); return; }
    pending.current ??= { key: crypto.randomUUID(), user: selection.userId, action, body: { attemptId: selection.attemptId, expectedGeneration: selection.resetGeneration, reason: reason.trim(), confirmation: action } };
    const operation = pending.current; sending.current = true; setBusy(true); setError('');
    try {
      await request(`/admin/tap-attempts/members/${operation.user}/${operation.action === 'RESTORE' ? 'restore' : 'retry'}`, 'POST', operation.body, { headers: { 'Idempotency-Key': operation.key } });
      pending.current = null; setSelection(null); setUncertain(false); setNotice(operation.action === 'RESTORE' ? '원래 기록으로 F를 복구했어요. 전체 수집 순위도 인정 시각으로 다시 계산돼요.' : 'F 재도전을 허용했어요. 참가자는 게임 화면을 새로고침해 주세요.'); refresh();
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : '변경 결과를 확인하지 못했어요.';
      if (failure instanceof ApiError && [401, 403].includes(failure.status)) { onDenied(message); return; }
      setError(message); const unknown = !(failure instanceof ApiError) || failure.status >= 500;
      setUncertain(unknown); setBlocked(failure instanceof ApiError && failure.status === 409); if (!unknown) pending.current = null;
    } finally { sending.current = false; setBusy(false); }
  }
  return <section className="admin-panel tap-support">
    <div className="admin-panel-heading"><h2>FAST TAP 지원</h2><Button variant="secondary" disabled={busy || !!selection} onClick={refresh}>목록 새로고침</Button></div>
    <p className="admin-muted">복구 가능한 원본 기록은 당시 접수 시각으로 F를 인정하고 전체 수집 순위를 보정합니다. 나머지 실패자만 재도전하며 태그 해금과 다른 미션은 유지합니다.</p>
    <p className="admin-muted">입력 패턴은 참고 신호이며 부정행위 판정이 아닙니다. 신호만으로 F나 순위를 차단하지 않습니다.</p>
    <form className="tap-support-filters" onSubmit={event => { event.preventDefault(); go(0, filter, new FormData(event.currentTarget).get('q')?.toString() || ''); }}>
      <label htmlFor="tap-support-search">아이디·이름·닉네임</label><input id="tap-support-search" name="q" maxLength={64} defaultValue={q} key={q} disabled={!!selection} />
      <Button variant="secondary" type="submit" disabled={!!selection}>검색</Button>
      <Button variant={filter === 'FAILED' ? 'primary' : 'secondary'} type="button" disabled={!!selection} aria-pressed={filter === 'FAILED'} onClick={() => go(0, 'FAILED')}>실패한 도전</Button>
      <Button variant={filter === 'ALL' ? 'primary' : 'secondary'} type="button" disabled={!!selection} aria-pressed={filter === 'ALL'} onClick={() => go(0, 'ALL')}>전체 현재 도전</Button>
    </form>
    {loading && <p role="status">도전 목록을 불러오고 있어요.</p>}{notice && <p role="status">{notice}</p>}{!selection && error && <p role="alert">{error}</p>}
    {page && <><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>참가자</th><th>현재 도전</th><th>기록·사유</th><th>입력 패턴 참고</th><th>운영 조치</th></tr></thead><tbody>{page.items.map(row => <tr key={row.attemptId}>
      <td data-label="참가자"><Link href={`/admin/members/${row.userId}`} className="admin-identity">{row.nickname || row.realName || row.username}</Link><small>{row.username} · {row.region} {row.classNo ? `${row.classNo}반` : ''}</small></td>
      <td data-label="현재 도전">{states[row.status] || row.status}<small>시작 {date(row.startsAt)}<br />제출 기한 {date(row.submitDeadline)}</small></td>
      <td data-label="기록·사유">{row.tapCount === null ? '제출 기록 없음' : `${row.tapCount}회`}<small>{row.reason ? reasons[row.reason] || '기존 판정 사유 있음' : row.status === 'EXPIRED' ? '유예 안에 접수된 기록 없음' : '—'}<br />접수 {date(row.submittedAt)}</small></td>
      <td data-label="입력 패턴 참고">{row.signals.codes.length ? row.signals.codes.map(code => <small key={code}>{signals[code] || code}</small>) : '특이 신호 없음'}{row.signals.codes.length > 0 && <small>동일 시각 {row.signals.sameTimestampPairs}쌍 · 반복 비율 {(row.signals.intervalSharePermille / 10).toFixed(1)}%</small>}</td>
      <td data-label="운영 조치">{row.canRestore ? <Button variant="secondary" disabled={!!selection} onClick={() => choose(row, 'RESTORE')}>기존 기록 복구</Button> : <Button variant="secondary" disabled={!row.retryAllowed || !!selection} onClick={() => choose(row, 'RETRY')}>재도전 허용</Button>}{!row.retryAllowed && !row.canRestore && <small>진행 중·완료 기록은 제외</small>}</td>
    </tr>)}</tbody></table>{!page.items.length && <p className="admin-empty">조건에 맞는 도전이 없어요.</p>}</div>
    <div className="admin-pager"><span>{page.total.toLocaleString('ko-KR')}명</span><div><Button variant="quiet" disabled={offset === 0 || !!selection} onClick={() => go(Math.max(0, offset - 25))}>이전</Button><Button variant="quiet" disabled={page.nextOffset === null || !!selection} onClick={() => go(page.nextOffset!)}>다음</Button></div></div></>}
    <Dialog open={!!selection} title={action === 'RESTORE' ? 'FAST TAP 기존 기록 복구' : 'FAST TAP 재도전 허용'} onClose={close}>
      <form className="admin-reset-form" onSubmit={retry} aria-busy={busy} noValidate>
        <p><strong>{selection?.nickname || selection?.username}</strong> · F 도전 #{selection?.attemptId}</p><p>{action === 'RESTORE' ? '원본 입력·해시·접수 시각을 재검증해 기존 점수와 F를 복구합니다. 전체 완료 순위는 다섯 미션의 마지막 인정 시각으로 다시 계산합니다.' : '실패한 F 기록을 보관하고 다시 도전하게 합니다. 기존 태그 해금·프로필·다른 게임은 유지합니다.'}</p>
        <label htmlFor="tap-retry-reason">{word} 사유</label><textarea id="tap-retry-reason" maxLength={300} rows={3} value={reason} disabled={busy || uncertain || blocked} onChange={event => setReason(event.target.value)} />
        <label htmlFor="tap-retry-confirm">확인란에 ‘{word}’{action === 'RESTORE' ? '를' : '을'} 입력해 주세요</label><input id="tap-retry-confirm" value={confirmation} disabled={busy || uncertain || blocked} onChange={event => setConfirmation(event.target.value)} autoComplete="off" />
        {error && <p role="alert">{error}</p>}{uncertain && <p>응답을 확인하지 못했어요. 대상과 사유를 유지한 같은 요청으로 결과를 확인해 주세요.</p>}
        {blocked ? <Button type="button" onClick={() => { setSelection(null); setError(''); refresh(); }}>목록 다시 확인</Button> : <Button type="submit" disabled={busy}>{busy ? '처리 중…' : uncertain ? '같은 요청 다시 확인' : action === 'RESTORE' ? '기존 기록 복구하기' : '재도전 허용하기'}</Button>}
        <Button type="button" variant="quiet" disabled={busy || uncertain} onClick={close}>취소</Button>
      </form>
    </Dialog>
  </section>;
}
