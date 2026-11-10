import { ApiError, request } from '../auth/api';
import type { AwardCategory, AwardProof } from './awards-api';
export interface ResultMetric { key: string; value: number; unit: string; basis: string }
export interface ResultMission { code: string; unlocked: number | null; performed: number; completed: number; pending: number; basis: string }
export interface ResultData {
  window: { observedAt: string; opensAt: string | null; closesAt: string | null; moderationClosesAt: string | null };
  phase: string; scope: string; metrics: ResultMetric[]; missions: ResultMission[];
  buckets: { group: string; label: string; count: number }[];
  hours: { hourUtc: string; event: string; participants: number; records: number }[]; hoursTruncated: boolean;
  candidates: { category: AwardCategory; rank: number; participantId: string; score: number | null; validCompletionAt: string; profileRegisteredAt: string; metricTie: boolean; unresolvedTie: boolean; overlappingTopCategories: AwardCategory[]; proofs: AwardProof[] }[];
  reviewRecords: { participantId: string; sourceId: number; reason: string }[]; reviewTotal: number;
  policyDecisions: string[]; limitations: string[]; finalizationAvailable: false;
}
export interface ResultReport { ruleVersion: string; snapshotId: string; data: ResultData }
export interface ActivityHistory { participantId: string; observedAt: string; purpose: string; profileFirstPublishedAt: string | null; missions: { mission: string; status: string; unlockedAt: string | null; completedAt: string | null; sourceType: string | null; sourceId: number | null; acceptedAt: string | null; validProof: boolean }[]; receipts: { cursor: number; eventType: string; missionCode: string | null; eventTime: string; status: string; sourceType: string }[]; nextCursor: number | null; limitation: string }
export function resultChanges(previous: ResultReport | null, next: ResultReport) {
  if (!previous) return [];
  const old = new Map(previous.data.metrics.map(m => [m.key, m.value]));
  return next.data.metrics.filter(m => old.has(m.key) && old.get(m.key) !== m.value).map(m => ({ key: m.key, before: old.get(m.key)!, after: m.value }));
}
export const resultsApi = {
  report: () => request<ResultReport>('/admin/results'),
  history: (id: number, after = 0) => request<ActivityHistory>(`/admin/results/participants/${id}?purpose=RESULT_RECONCILIATION&after=${after}`),
  async download(format: 'csv' | 'json' | 'md', snapshot: string) {
    let response: Response;
    try { response = await fetch(`/api/v1/admin/results/export?format=${format}&snapshot=${encodeURIComponent(snapshot)}`, { credentials: 'same-origin', cache: 'no-store' }); }
    catch { throw new ApiError(503, 'NETWORK_ERROR', '다운로드에 연결하지 못했어요. 다시 시도해 주세요.'); }
    if (!response.ok) { const failure = await response.json().catch(() => ({})); throw new ApiError(response.status, failure.code || 'EXPORT_FAILED', failure.message || '다운로드 권한과 최신 집계를 확인해 주세요.'); }
    return response.blob();
  },
};
