import { request } from '../auth/api';
import type { MissionCode } from '../missions/model';
export type PolicyType = 'PARTICIPATION' | 'PROFILE_PUBLICATION';
export interface Policy { type: PolicyType; version: string; title: string; body: string; agreed: boolean }
export interface Policies { ready: boolean; policies: Policy[] }
export interface EventState { status: 'NOT_CONFIGURED' | 'BEFORE_OPEN' | 'OPEN' | 'CLOSED'; opensAt: string | null; submitClosesAt: string | null; profilePreparationEnabled: boolean }
export interface Profile { nickname: string; realName: string; region: string; classNo: number; bio: string; githubUrl: string | null; blogUrl: string | null; isPublic: boolean; version: number; interestIds: number[] }
export interface ProfileState { profile: Profile | null; consents: Policies; profileComplete: boolean; participationReady: boolean }
export interface Interest { id: number; name: string }
export interface ServerMission { id: number; code: MissionCode; letter: string; title: string; hint: string; active: boolean; state: 'LOCKED' | 'UNLOCKED' | 'IN_PROGRESS' | 'COMPLETED'; unlockedAt: string | null; completedAt: string | null }
export interface Completion { complete: boolean; completedAt: string | null; completionOrder: number | null; awardConfirmed: boolean }
export interface Collection { missions: ServerMission[]; completedCount: number; completion: Completion }
export interface TagResult { missionCode: MissionCode; state: ServerMission['state']; route: string; newlyUnlocked: boolean; newlyCompleted: boolean }
export interface ProfileInput { nickname: string; realName: string; classNo: number; bio: string; githubUrl: string | null; blogUrl: string | null; isPublic: boolean; expectedVersion: number; interestIds: number[]; acceptPolicies: { type: PolicyType; version: string }[] }
export const participationApi = {
  event: () => request<EventState>('/event'),
  collection: () => request<Collection>('/missions/collection'),
  profile: () => request<ProfileState>('/users/me/profile'),
  interests: () => request<Interest[]>('/interests'),
  saveProfile: (value: ProfileInput) => request<ProfileState>('/users/me/profile', 'PUT', value),
  withdraw: (type: PolicyType, version: string) => request<void>(`/users/me/consents/${type}`, 'PUT', { version, agreed: false }),
  pending: () => request<{ present: boolean }>('/tags/pending'),
  rememberTag: (token: string) => request<void>('/tags/pending', 'POST', { token }),
  resolve: (token: string) => request<TagResult>('/tags/resolve', 'POST', { token }),
  resume: () => request<TagResult>('/tags/resume', 'POST', {}),
};
