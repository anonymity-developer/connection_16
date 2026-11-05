import { request } from '../auth/api';
export type ContentKind = 'wishes' | 'cheers';
export type Template = 'PLAIN' | 'NOTE';
export type Style = 'ROSE' | 'INK';
export interface ContentInput { content: string; templateId: Template; style: Style; anonymous: boolean; expectedVersion: number | null }
export interface Revision { id: number; itemId: number; number: number; content: string; templateId: Template; style: Style; anonymous: boolean; submittedAt: string; status: 'QUEUED' | 'PROCESSING' | 'APPROVED' | 'REJECTED' | 'REVIEW_REQUIRED' | 'SUPERSEDED'; reason: string | null }
export interface OwnSlot { id: number | null; version: number; visibility: 'VISIBLE' | 'HIDDEN' | null; approvedRevision: Revision | null; pendingRevision: Revision | null; latestRevision: Revision | null; missionCompleted: boolean; luckScore: number | null; landingZone: number | null }
export interface PublicContent { profileId: number | null; id: number; content: string; templateId: Template; style: Style; anonymous: boolean; nickname: string | null; luckScore: number | null; landingZone: number | null }
export interface Feed { items: PublicContent[]; nextCursor: number | null }
export const contentApi = {
  own: (kind: ContentKind) => request<OwnSlot>(`/${kind}/me`),
  feed: (kind: ContentKind, cursor?: number) => request<Feed>(`/${kind}${cursor ? `?cursor=${cursor}` : ''}`),
  submit: (kind: ContentKind, editing: boolean, key: string, input: ContentInput) => request<OwnSlot>(`/${kind}${editing ? '/me' : ''}`, editing ? 'PUT' : 'POST', input, { headers: { 'Idempotency-Key': key } }),
};
