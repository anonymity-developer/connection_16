import { useEffect, useState } from 'react';
import { request } from '../auth/api';
import { Button } from '../../components/ui';

interface FunnelReport {
  mission: string; questionnaireVersion: string | null; collectionStartedAt: string; generatedAt: string;
  steps: { eventType: string; step: number; users: number; dropOffRate: number | null; conversionRate: number | null }[];
}
const date = (value: string) => new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul' }).format(new Date(value));
const rate = (value: number | null) => value === null ? '—' : `${value.toFixed(1)}%`;

export function FunnelPanel() {
  const [data, setData] = useState<FunnelReport | null>(null), [error, setError] = useState('');
  const [busy, setBusy] = useState(true), [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true; setBusy(true); setError('');
    request<FunnelReport>('/admin/funnel').then(value => { if (active) setData(value); })
      .catch(failure => { if (active) { setData(null); setError(failure instanceof Error ? failure.message : '퍼널 현황을 불러오지 못했어요.'); } })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [revision]);
  return <section className="admin-panel" aria-label="SYNC ME 퍼널 집계" aria-busy={busy}>
    <div className="admin-panel-heading"><h2>질문별 참여 흐름</h2><Button variant="secondary" disabled={busy} onClick={() => setRevision(value => value + 1)}>새로고침</Button></div>
    {busy && <p role="status">퍼널 현황을 불러오고 있어요.</p>}
    {error && <p role="alert" className="admin-error">{error}</p>}
    {data && <>
      <p className="admin-muted">수집 시작 {date(data.collectionStartedAt)} KST · 조회 {date(data.generatedAt)} KST</p>
      <p className="admin-muted">현재 문항 버전 {data.questionnaireVersion ?? '미설정'} · 현재 활성 참가자의 초기화 이후 기록</p>
      <div className="admin-table-wrap"><table className="admin-table admin-funnel-table"><thead><tr><th>단계</th><th className="num">고유 사용자</th><th className="num">직전 단계 대비 이탈률</th><th className="num">시작 대비 전환율</th></tr></thead>
        <tbody>{data.steps.map(row => <tr key={row.step}>
          <td data-label="단계">{row.eventType === 'SCREEN_ENTER' ? '설문 화면 진입' : row.eventType === 'SUBMIT_SUCCESS' ? '제출 완료' : `질문 ${row.step}`}</td>
          <td data-label="고유 사용자" className="num">{row.users.toLocaleString('ko-KR')}명</td>
          <td data-label="직전 단계 대비 이탈률" className="num">{rate(row.dropOffRate)}</td>
          <td data-label="시작 대비 전환율" className="num">{rate(row.conversionRate)}</td>
        </tr>)}</tbody></table></div>
      {(!data.steps.length || data.steps.every(row => row.users === 0)) && <p className="admin-empty">아직 집계할 참여 기록이 없어요.</p>}
    </>}
    <p className="admin-note">수집 시작 이전의 화면·질문 조회는 소급할 수 없습니다. 재방문·이전 질문 이동·중복 조회는 사용자별로 한 번만 집계합니다. 조회 이벤트 누락으로 단계별 인원이 역전될 수 있으며, 제출 완료는 서버 저장 기록을 기준으로 합니다.</p>
    <p className="admin-muted">단계마다 독립적으로 센 인원입니다. 이탈률은 (직전 단계 인원 − 현재 단계 인원) ÷ 직전 단계 인원, 전환율은 현재 단계 인원 ÷ 화면 진입 인원입니다. 분모가 0이면 —, 인원이 역전되면 음수 이탈률이나 100% 초과 전환율이 표시됩니다. 초기화 이전 회차와 비활성 계정, 이전 문항 버전은 제외합니다. 수집 시작 이후 저장된 현재 설문 결과만 제출 완료로 셉니다.</p>
  </section>;
}
