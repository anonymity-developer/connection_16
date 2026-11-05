import { request } from '../auth/api';
export interface LikeState { liked: boolean; count: number; canLike: boolean }
export interface PublicComment { profileId: number | null; id: number; content: string; revealProfile: boolean; nickname: string | null }
export interface OwnComment { id: number; content: string; revealProfile: boolean; status: 'QUEUED' | 'PROCESSING' | 'APPROVED' | 'REJECTED' | 'REVIEW_REQUIRED' | 'SUPERSEDED'; reason: string | null; submittedAt: string; requestKey: string }
export interface CommentPage<T> { items: T[]; nextCursor: number | null }
export const reactionsApi = {
  hearts: (ids: number[]) => request<Record<number, LikeState>>(`/cheers/likes?ids=${ids.join(',')}`),
  heart: (id: number) => request<LikeState>(`/cheers/${id}/like`),
  async setHeart(id: number, desired: boolean) { if (desired) return request<LikeState>(`/cheers/${id}/like`, 'PUT'); await request<void>(`/cheers/${id}/like`, 'DELETE'); return request<LikeState>(`/cheers/${id}/like`); },
  comments: (id: number, cursor?: number) => request<CommentPage<PublicComment>>(`/wishes/${id}/comments${cursor ? `?cursor=${cursor}` : ''}`),
  own: (id: number, cursor?: number) => request<CommentPage<OwnComment>>(`/wishes/${id}/comments/me${cursor ? `?cursor=${cursor}` : ''}`),
  submit: (id: number, key: string, content: string, revealProfile: boolean) => request<OwnComment>(`/wishes/${id}/comments`, 'POST', { content, revealProfile }, { headers: { 'Idempotency-Key': key } }),
};
