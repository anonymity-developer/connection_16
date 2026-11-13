import { request } from '../auth/api';
export type ContentKind = 'wishes' | 'cheers';
export type Template = 'PLAIN' | 'NOTE';
export type Style = 'ROSE' | 'INK' | 'PEACH' | 'LEMON' | 'MINT' | 'SAGE' | 'LAVENDER' | 'LILAC' | 'SAND' | 'CLOUD';
export type CheerSort = 'LATEST' | 'HEARTS';
export type SortDirection = 'ASC' | 'DESC';
export type FeedCursor = number | string;
export interface ContentInput { content: string; templateId: Template; style: Style; anonymous: boolean; expectedVersion: number | null }
export interface Revision { id: number; itemId: number; number: number; content: string; templateId: Template; style: Style; anonymous: boolean; submittedAt: string; status: 'QUEUED' | 'PROCESSING' | 'APPROVED' | 'REJECTED' | 'REVIEW_REQUIRED' | 'SUPERSEDED'; reason: string | null }
export interface OwnSlot { id: number | null; version: number; visibility: 'VISIBLE' | 'HIDDEN' | null; approvedRevision: Revision | null; pendingRevision: Revision | null; latestRevision: Revision | null; missionCompleted: boolean; luckScore: number | null; landingZone: number | null }
export interface PublicContent { profileId: number | null; id: number; content: string; templateId: Template; style: Style; anonymous: boolean; nickname: string | null; luckScore: number | null; landingZone: number | null }
export interface Feed { items: PublicContent[]; nextCursor: FeedCursor | null }
export const contentApi = {
  own: (kind: ContentKind) => request<OwnSlot>(`/${kind}/me`),
  feed: (kind: ContentKind, cursor?: FeedCursor, sort: CheerSort = 'LATEST', direction: SortDirection = 'DESC') => {
    const query = new URLSearchParams();
    if (cursor) query.set('cursor', String(cursor));
    if (kind === 'cheers') { query.set('sort', sort); query.set('direction', direction); }
    return request<Feed>(`/${kind}${query.size ? `?${query}` : ''}`);
  },
  submit: (kind: ContentKind, editing: boolean, key: string, input: ContentInput) => request<OwnSlot>(`/${kind}${editing ? '/me' : ''}`, editing ? 'PUT' : 'POST', input, { headers: { 'Idempotency-Key': key } }),
};
