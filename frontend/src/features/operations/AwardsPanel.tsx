import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, navigate } from '../../app/navigation';
import { Button } from '../../components/ui';
import { ApiError } from '../auth/api';
import { awardLabels, awardsApi } from './awards-api';
import type { AwardCategory, AwardReport } from './awards-api';
import { missions } from '../missions/model';
const time = (value: string | null) => value ? new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul' }).format(new Date(value)) + ` (${value})` : '미설정';
const labels = Object.keys(awardLabels) as AwardCategory[];
export function AwardsPanel({ onDenied }: { onDenied: (message: string) => void }) {
  const location = useLocation(), params = new URLSearchParams(location.split('?')[1]);
  const category = labels.includes(params.get('category') as AwardCategory) ? params.get('category') as AwardCategory : 'FIRST_COMPLETION';
  const offset = /^\d{1,6}$/.test(params.get('offset') || '') ? Number(params.get('offset')) : 0;
  const [report, setReport] = useState<AwardReport | null>(null), [error, setError] = useState(''), [loading, setLoading] = useState(true);
  const generation = useRef(0);
  async function load() {
    const version = ++generation.current; setLoading(true); setReport(null); setError('');
    try { const next = await awardsApi.candidates(category, offset); if (version === generation.current) setReport(next); }
    catch (failure) { if (version === generation.current) { const message = failure instanceof Error ? failure.message : '시상 후보를 확인하지 못했어요.'; if (failure instanceof ApiError && (failure.status === 401 || failure.status === 403)) onDenied(message); else setError(message); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  useEffect(() => { void load(); return () => { generation.current++; }; }, [location]);
  return <section className="ops-awards" aria-labelledby="award-heading" aria-busy={loading}>
    <div className="ops-toolbar"><div><h2 id="award-heading">잠정 시상 후보</h2><p className="ops-meta">경쟁 4부문 · 각 1명 기준 · 다섯 미션 유효 완료자 대상</p></div><Button variant="secondary" onClick={load} disabled={loading}>후보 다시 조회</Button></div>
    <div className="award-category"><label htmlFor="award-category">시상 부문</label><select id="award-category" value={category} onChange={e => navigate(`/admin/moderation?tab=awards&category=${e.target.value}`)}>{labels.map(c => <option key={c} value={c}>{awardLabels[c]}</option>)}</select></div>
    {loading && <p role="status">서버 근거를 같은 조회 시점으로 집계하고 있어요.</p>}
    {error && <p role="alert" className="form-notice is-error">{error}</p>}
    {report && <AwardReportView report={report} />}
  </section>;
}
export function AwardReportView({ report }: { report: AwardReport }) {
  return <><div className="award-basis"><strong>잠정 후보 · 수상 확정 아님</strong><p>조회 {time(report.observedAt)} · 기준 {report.ruleVersion}</p><p>제출 마감 {time(report.submitClosesAt)}<br />검수 마감 {time(report.moderationClosesAt)}</p><p>{report.eventPhase !== 'REVIEW' ? '행사 설정 또는 참여·검수가 진행 중이에요. 결과가 바뀔 수 있어요.' : '검수 마감 후에도 숨김·정정에 따라 후보가 바뀔 수 있어요.'}</p><p>유효 전체 완료 {report.eligibleCompleteCount}명 · 해당 부문 {report.total}명</p></div>
    <p className="ops-meta">정렬: {report.category === 'FIRST_COMPLETION' ? '다섯 유효 근거의 가장 늦은 인정 접수 시각 오름차순' : '유효 점수 내림차순'} → 최초 프로필 등록 시각 오름차순. 완전 동점은 공동 순위예요.</p>
    {!report.items.length ? <p className="ops-message">이 조회 범위의 후보가 없어요. 설정과 다섯 미션의 유효 근거를 확인해 주세요.</p> : <ol className="award-candidates">{report.items.map(row => <li key={row.userId}><div className="award-row"><strong>{row.rank}위 · {row.nickname} <small>#{row.userId}</small></strong><span>{row.score === null ? time(row.validCompletionAt) : `${row.score.toLocaleString('ko-KR')} ${report.category === 'COMMENT_KING' ? '개 소원' : report.category === 'POPULAR_CHEER' ? '하트' : '탭'}`}</span></div>
      {row.metricTie && <p className="ops-meta">동점 기록 · 최초 프로필 등록 {time(row.profileRegisteredAt)}</p>}{row.unresolvedTie && <p className="award-warning">공동 순위 · 추가 동점 기준 결정 필요</p>}{row.score === 0 && <p className="award-warning">유효 활동 점수 0 · 최소 활동 조건 확인 필요</p>}{row.overlappingTopCategories.length > 1 && <p className="award-warning">복수 부문 1위 후보: {row.overlappingTopCategories.map(c => awardLabels[c]).join(' · ')} — 배정하지 않음</p>}
      <details><summary>산정 근거 보기</summary><p>최초 프로필 등록 {time(row.profileRegisteredAt)}<br />유효 전체 완성 {time(row.validCompletionAt)}</p><ul>{row.proofs.map(proof => <li key={proof.mission}>{missions.find(m => m.code === proof.mission)?.name || proof.mission} · {proof.sourceKind} 근거 #{proof.sourceId} / 접수 #{proof.receiptOrder}<br />인정 접수 {time(proof.acceptedAt)}</li>)}</ul></details></li>)}</ol>}
    <nav className="award-pagination" aria-label="시상 후보 페이지">{report.offset > 0 && <Link className="text-link" href={`/admin/moderation?tab=awards&category=${report.category}&offset=${Math.max(0, report.offset - report.limit)}`}>이전 후보</Link>}{report.offset + report.limit < report.total && <Link className="text-link" href={`/admin/moderation?tab=awards&category=${report.category}&offset=${report.offset + report.limit}`}>다음 후보</Link>}</nav>
    <section className="award-review"><h3>정산 전 확인</h3><p>미처리 콘텐츠 검수 {report.pendingModeration}건 · 유효하지 않은 완료 근거 {report.invalidProofs}건 · 다섯 유효 근거 미충족 {report.incompleteProofSets}명</p><p>근거·게임 검토 필요 {report.reviewTotal}건 (최대 50건 표시, 후보 점수에서 제외)</p>{report.reviewRecords.length > 0 && <ul>{report.reviewRecords.map(r => <li key={r.sourceId}>참가자 #{r.userId} · 근거 #{r.sourceId || '없음'} · {r.reason === 'TAP_REVIEW_REQUIRED' ? 'FAST TAP 검토 대기' : r.reason.startsWith('INVALID_') ? '완료 근거 불일치' : '현재 참가 자격 확인 필요'}</li>)}</ul>}<Link className="text-link" href="/admin/moderation?tab=moderation">콘텐츠 검수 확인</Link><ul>{report.pendingDecisions.map(note => <li key={note}>{note}</li>)}</ul><p>랜덤 참여상은 미설정이며 추첨·확정·지급 기능은 제공하지 않아요.</p></section>
  </>;
}
