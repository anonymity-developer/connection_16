import { request } from '../auth/api';
export interface SyncQuestions { available: boolean; version: string | null; title: string | null; questions: { id: string; text: string; options: { id: string; label: string }[] }[] }
export interface SyncResult { questionnaireVersion: string; type: { code: string; title: string; description: string }; axisScores: Record<string, number>; submittedAt: string }
export interface TapAttempt { attemptId: number; generation: number; status: 'COUNTDOWN' | 'PLAYING' | 'AWAITING_SUBMISSION' | 'CANCELLED' | 'INTERRUPTED' | 'VALID' | 'INVALID' | 'REVIEW_REQUIRED' | 'EXPIRED'; serverNow: string; startsAt: string; endsAt: string; submitDeadline: string; tapCount: number | null; reviewReason: string | null; provisional: boolean }
export interface TapState { officialAvailable: boolean; attempt: TapAttempt | null }
export interface TapPayload { attemptId: number; taps: number[]; interruptions: { type: 'HIDDEN' | 'PAGE_HIDE'; atMs: number }[] }
export const resultsApi = {
  sync: () => request<{ result: SyncResult | null }>('/sync/me'),
  questions: () => request<SyncQuestions>('/sync/questions'),
  submitSync: (questionnaireVersion: string, answers: { questionId: string; optionId: string }[]) => request<SyncResult>('/sync/submit', 'POST', { questionnaireVersion, answers }),
  tap: () => request<TapState>('/tap/me'),
  start: (key: string) => request<TapAttempt>('/tap/start', 'POST', undefined, { headers: { 'Idempotency-Key': key } }),
  cancel: (attemptId: number) => request<TapAttempt>('/tap/cancel', 'POST', { attemptId }, { keepalive: true }),
  finish: (payload: TapPayload) => request<TapAttempt>('/tap/finish', 'POST', payload),
};
