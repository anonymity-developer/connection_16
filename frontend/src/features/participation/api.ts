import { request } from '../auth/api';
import type { MissionCode } from '../missions/model';
export type PolicyType = 'PARTICIPATION' | 'PROFILE_PUBLICATION';
export interface Policy { type: PolicyType; version: string; title: string; body: string; agreed: boolean }
export interface Policies { ready: boolean; policies: Policy[] }
export interface EventState { status: 'NOT_CONFIGURED' | 'BEFORE_OPEN' | 'OPEN' | 'CLOSED'; opensAt: string | null; submitClosesAt: string | null; profilePreparationEnabled: boolean }
export interface Profile { nickname: string; bio: string | null; githubUrl: string | null; blogUrl: string | null; version: number; interestIds: number[] }
export interface ProfileIdentity { realName: string | null; region: string | null; classNo: number | null; photoUrl: string | null; ready: boolean }
export interface ProfileState { identity: ProfileIdentity; profile: Profile | null; consents: Policies; profileComplete: boolean; participationReady: boolean }
export interface Interest { id: number; name: string; category?: 'INDUSTRY' | 'DEVELOPMENT' | 'DOMAIN'; groupName?: string | null }
export interface ServerMission { id: number; code: MissionCode; letter: string; title: string; hint: string; active: boolean; state: 'LOCKED' | 'UNLOCKED' | 'IN_PROGRESS' | 'COMPLETED'; unlockedAt: string | null; completedAt: string | null }
export interface Completion { complete: boolean; completedAt: string | null; completionOrder: number | null; awardConfirmed: boolean }
export interface Collection { missions: ServerMission[]; completedCount: number; completion: Completion; resetGeneration?: number }
export interface TagResult { missionCode: MissionCode; state: ServerMission['state']; route: string; newlyUnlocked: boolean; newlyCompleted: boolean }
export interface TagInspection { missionCode: MissionCode; letter: string; title: string }
export interface ProfileInput { nickname: string; bio: string | null; githubUrl: string | null; blogUrl: string | null; expectedVersion: number; interestIds: number[]; acceptPolicies: { type: PolicyType; version: string }[] }
export const participationApi = {
  event: () => request<EventState>('/event'),
  collection: () => request<Collection>('/missions/collection'),
  reconcileShowMe: () => request<{ newlyCompleted: boolean }>('/missions/show-me/reconcile', 'POST', {}),
  profile: () => request<ProfileState>('/users/me/profile'),
  interests: () => request<Interest[]>('/interests'),
  saveProfile: (value: ProfileInput) => request<ProfileState>('/users/me/profile', 'PUT', value),
  pending: () => request<{ present: boolean }>('/tags/pending'),
  rememberTag: (token: string) => request<void>('/tags/pending', 'POST', { token }),
  inspectTag: (token?: string) => request<TagInspection>('/tags/inspect', 'POST', token ? { token } : {}),
  resolve: (token: string) => request<TagResult>('/tags/resolve', 'POST', { token }),
  resume: () => request<TagResult>('/tags/resume', 'POST', {}),
};
