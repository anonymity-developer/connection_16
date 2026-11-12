import { request } from '../auth/api';
import type { Interest } from '../participation/api';
export interface Person { id: number; nickname: string; bio?: string | null; interests: Interest[]; type: { code: string; title: string; version: string } | null; sharedInterests: string[]; sameType: boolean; visited: boolean; visitedMe: boolean; mutual: boolean }
export interface RecommendationBatch { batchId: string; items: Person[] }
export interface People { items: Person[]; nextCursor: number | null }
export interface Detail { person: Person; realName: string; region: string; classNo: number; bio: string | null; githubUrl: string | null; blogUrl: string | null; photoUrl: string | null; newlyVisited: boolean }
export interface Stats { visitedCount: number; visitorCount: number; mutualCount: number }
export interface Graph { self: { id: number; nickname: string }; nodes: Person[]; edges: { from: number; to: number; weight?: number; strength?: string }[]; truncated: boolean }
export const discoveryApi = {
  people: (filters: URLSearchParams, cursor?: number) => { const query = new URLSearchParams(); for (const key of ['q', 'interestId']) { const value = filters.get(key); if (value) query.set(key, value); } for (const key of ['industryIds','developmentIds','stackIds']) for (const value of filters.getAll(key)) query.append(key,value); if (cursor) query.set('cursor', String(cursor)); return request<People>(`/discovery/people?${query}`); },
  recommended: (seed?: string) => request<Person[]>(`/discovery/recommendations${seed ? `?seed=${encodeURIComponent(seed)}` : ''}`),
  batch: (seed?: string) => request<RecommendationBatch>(`/discovery/recommendations/batch${seed ? `?seed=${encodeURIComponent(seed)}` : ''}`),
  expose: (batchId: string, profileIds: number[]) => request<void>('/discovery/recommendations/exposures','POST',{batchId,profileIds},{keepalive:true}),
  open: (id: number) => request<Detail>(`/discovery/profiles/${id}/open`, 'POST'),
  stats: () => request<Stats>('/discovery/stats'),
  graph: (offset = 0, relationship = 'all') => request<Graph>(`/discovery/graph?limit=30&offset=${offset}&relationship=${relationship}`),
};
