import { useEffect, useState } from 'react';
import { request } from '../auth/api';
import { Button } from '../../components/ui';
import './discovery-conversion.css';

interface ConversionReport {
  basis: 'CURRENT_SYNC_RESULTS_ALL_VERSIONS';
  generatedAt: string;
  steps: { code: string; users: number; previousConversionRate: number | null; completionConversionRate: number | null }[];
}
const labels: Record<string, string> = {
  SYNC_COMPLETED: 'SYNC ME 완료',
  PROFILE_FIRST_VISIT: '완료 후 새 동료 프로필 방문',
  MUTUAL_VISIT: '해당 동료와 상호 방문 형성',
};
const date = (value: string) => new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul' }).format(new Date(value));
const rate = (value: number | null) => value === null ? '—' : `${value.toFixed(1)}%`;

export function DiscoveryConversionPanel() {
  const [data, setData] = useState<ConversionReport | null>(null), [error, setError] = useState('');
  const [busy, setBusy] = useState(true), [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true; setBusy(true); setError('');
    request<ConversionReport>('/admin/discovery-conversion')
      .then(value => { if (active) setData(value); })
      .catch(failure => { if (active) { setData(null); setError(failure instanceof Error ? failure.message : '전환 분석을 불러오지 못했어요.'); } })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [revision]);
  return <section className="admin-panel admin-conversion-panel" aria-label="동료 탐색 전환 집계" aria-busy={busy}>
    <div className="admin-panel-heading"><h2>SYNC ME 이후 탐색 흐름</h2><Button variant="secondary" disabled={busy} onClick={() => setRevision(value => value + 1)}>새로고침</Button></div>
    {busy && <p role="status">전환 분석을 불러오고 있어요.</p>}
    {error && <p role="alert" className="admin-error">{error}</p>}
    {data && <p className="admin-muted">조회 {date(data.generatedAt)} KST · 사용자별 한 번 집계</p>}
    <div className="admin-table-wrap"><table className="admin-table admin-conversion-table"><thead><tr><th>단계</th><th className="num">고유 사용자</th><th className="num">직전 단계 대비 전환율</th><th className="num">SYNC ME 완료 대비 전환율</th></tr></thead>
      <tbody>{Object.entries(labels).map(([code, label]) => {
        const row = data?.steps.find(step => step.code === code);
        return <tr key={code}><td data-label="단계">{label}</td><td data-label="고유 사용자" className="num">{row ? `${row.users.toLocaleString('ko-KR')}명` : '—'}</td><td data-label="직전 단계 대비 전환율" className="num">{row ? rate(row.previousConversionRate) : '—'}</td><td data-label="SYNC ME 완료 대비 전환율" className="num">{row ? rate(row.completionConversionRate) : '—'}</td></tr>;
      })}</tbody></table></div>
    {data?.steps[0]?.users === 0 && <p className="admin-empty">아직 집계할 SYNC ME 완료 기록이 없어요.</p>}
    <p className="admin-note">현재 활성 참가자의 저장 결과와 모든 문항 버전을 집계합니다. 상대별 최초 방문 시각이 내 SYNC ME 제출 시각보다 늦은 경우만 포함합니다. 상호 방문은 같은 상대와 양방향 방문이 있고, 두 방향 중 늦은 시각에 형성된 것으로 봅니다.</p>
    <p className="admin-muted">프로필 열기 요청의 저장 기록이며 추천 노출·실제 클릭·실제 대화나 설문의 인과 효과를 뜻하지 않습니다. 완료 전 방문한 상대의 재방문은 구분할 수 없습니다. 초기화로 보관된 결과는 제외합니다.</p>
  </section>;
}
