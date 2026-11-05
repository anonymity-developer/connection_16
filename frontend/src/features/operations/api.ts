import { request } from '../auth/api';
import type { Revision } from '../content/api';
export interface Job { id: number; revisionId: number; authorId: number; itemId: number; status: string; attempts: number; fence: number; leaseUntil: string | null }
export interface Task { review: { job: Job; revision: Revision; approvedRevision: Revision | null }; kind: string; visibility: string; version: number; retryCount: number }
export interface Content { id: number; kind: string; authorId: number; visibility: string; version: number; parentWishId: number | null; approvedRevision: Revision | null; pendingRevision: Revision | null; latestRevision: Revision | null }
export interface Audit { id: number; actorId: number; action: string; targetType: string; targetId: number; reason: string; before: Record<string, unknown>; after: Record<string, unknown>; createdAt: string }
export interface Page<T> { items: T[]; nextCursor: number | null }
function query(values: Record<string, string | number | null | undefined>) { const q = new URLSearchParams(); Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') q.set(key, String(value)); }); return q.size ? `?${q}` : ''; }
export const operationsApi = {
  access: () => request<{ admin: boolean }>('/admin/access'),
  tasks: (status: string, after?: string) => request<Page<Task>>(`/admin/moderation/tasks${query({ status, after, limit: 20 })}`),
  task: (id: number) => request<Task>(`/admin/moderation/tasks/${id}`),
  contents: (kind?: string, visibility?: string, cursor?: string) => request<Page<Content>>(`/admin/content${query({ kind, visibility, cursor, limit: 20 })}`),
  content: (id: number) => request<Content>(`/admin/content/${id}`),
  audits: (action?: string, cursor?: string) => request<Page<Audit>>(`/admin/audit-logs${query({ action, cursor, limit: 20 })}`),
  decide: (id: number, expectedFence: number, decision: string, reason: string) => request<void>(`/admin/moderation/${id}/decision`, 'POST', { expectedFence, decision, reason }),
  retry: (id: number, expectedFence: number, reason: string) => request<void>(`/admin/moderation/${id}/retry`, 'POST', { expectedFence, reason }),
  visibility: (id: number, expectedVersion: number, visibility: string, reason: string) => request<void>(`/admin/content/${id}/visibility`, 'PATCH', { expectedVersion, visibility, reason }),
};
