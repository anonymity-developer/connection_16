import type { MissionRuntime } from './MissionRuntime';
import type { ContentKind, OwnSlot, Revision } from '../content/api';
import type { SyncResult, TapAttempt, TapState } from '../results/api';
import type { Interest, ProfileState } from '../participation/api';
import { ApiError } from '../auth/api';

export interface PreviewDefinition { version: string; source: 'CONFIGURED' | 'EXAMPLE'; definition: {
  title: string; axes: { id: string; negative: string; positive: string; tie: 'NEGATIVE' | 'POSITIVE' }[];
  questions: { id: string; text: string; options: { id: string; label: string; scores: Record<string, number> }[] }[];
  types: { code: string; title: string; description: string }[];
} }
const copy = <T>(value: T): T => structuredClone(value);
const empty = (): OwnSlot => ({ id: null, version: 0, visibility: null, approvedRevision: null, pendingRevision: null, latestRevision: null, missionCompleted: false, luckScore: null, landingZone: null });

// This client has no request/fetch dependency. Every action remains in this instance's memory.
export function createSandbox(definition: PreviewDefinition, interests: Interest[]) {
  let sync: SyncResult | null = null, tap: TapState = { officialAvailable: true, attempt: null };
  let serial = 0;
  const slots: Record<ContentKind, OwnSlot> = { wishes: empty(), cheers: empty() };
  let profile: ProfileState = { identity: { realName: '미리보기 예시', region: '서울', classNo: 1, photoUrl: null, ready: true }, profile: null,
    consents: { ready: true, policies: (['PARTICIPATION', 'PROFILE_PUBLICATION'] as const).map(type => ({ type, version: 'admin-preview', title: '관리자 미리보기 안내', body: '참여 화면 확인용 예시입니다. 실제 동의·프로필·완료 기록은 생성하지 않습니다.', agreed: false })) }, profileComplete: false, participationReady: false };
  const runtime: MissionRuntime = {
    refresh: async () => {},
    participation: {
      profile: async () => copy(profile), interests: async () => copy(interests),
      event: async () => ({ status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: true }),
      saveProfile: async input => {
        if (!input.nickname.trim() || input.nickname.trim().length > 30 || input.interestIds.length > 10) throw new ApiError(400, 'INVALID_PROFILE', '닉네임과 관심사를 확인해 주세요.');
        for (const [url, github] of [[input.githubUrl, true], [input.blogUrl, false]] as const) if (url) {
          let valid = false; try { const parsed = new URL(url); valid = parsed.protocol === 'https:' && !parsed.username && !parsed.password && (!github || parsed.hostname === 'github.com'); } catch { /* field error below */ }
          if (!valid) throw new ApiError(400, 'INVALID_URL', 'HTTPS 링크를 입력해 주세요. GitHub는 github.com 주소를 사용해 주세요.');
        }
        profile = { ...profile, profile: { ...input, version: (profile.profile?.version ?? 0) + 1 }, profileComplete: true, participationReady: true, consents: { ready: true, policies: profile.consents.policies.map(p => ({ ...p, agreed: true })) } };
        return copy(profile);
      },
    },
    results: {
      sync: async () => ({ result: copy(sync) }), questions: async () => ({ available: true, version: definition.version, title: definition.definition.title, questions: definition.definition.questions.map(q => ({ id: q.id, text: q.text, options: q.options.map(o => ({ id: o.id, label: o.label })) })) }),
      submitSync: async (version, answers) => {
        if (version !== definition.version || answers.length !== definition.definition.questions.length || new Set(answers.map(a => a.questionId)).size !== answers.length) throw new ApiError(400, 'INVALID_ANSWERS', '모든 문항을 확인해 주세요.');
        const totals = Object.fromEntries(definition.definition.axes.map(a => [a.id, 0]));
        for (const question of definition.definition.questions) {
          const answer = answers.find(a => a.questionId === question.id), option = question.options.find(o => o.id === answer?.optionId);
          if (!option) throw new ApiError(400, 'INVALID_ANSWERS', '모든 문항을 확인해 주세요.');
          for (const [axis, score] of Object.entries(option.scores)) totals[axis] += score;
        }
        const code = definition.definition.axes.map(a => totals[a.id] > 0 || totals[a.id] === 0 && a.tie === 'POSITIVE' ? a.positive : a.negative).join('');
        const type = definition.definition.types.find(t => t.code === code);
        if (!type) throw new Error('미리보기 유형 정의를 확인해 주세요.');
        sync ??= { questionnaireVersion: version, type, axisScores: totals, submittedAt: new Date().toISOString() }; return copy(sync);
      },
      tap: async () => copy(tap),
      start: async () => {
        if (tap.attempt) return copy(tap.attempt);
        const now = Date.now(); const attempt: TapAttempt = { attemptId: ++serial, generation: 1, status: 'COUNTDOWN', serverNow: new Date(now).toISOString(), startsAt: new Date(now + 5000).toISOString(), endsAt: new Date(now + 15000).toISOString(), submitDeadline: new Date(now + 20000).toISOString(), tapCount: null, reviewReason: null, provisional: false };
        tap = { officialAvailable: false, attempt }; return copy(attempt);
      },
      cancel: async id => {
        if (!tap.attempt || tap.attempt.attemptId !== id) throw new Error('미리보기 도전을 다시 시작해 주세요.');
        const attempt = { ...tap.attempt, status: Date.now() < Date.parse(tap.attempt.startsAt) ? 'CANCELLED' as const : 'INTERRUPTED' as const };
        tap = { officialAvailable: attempt.status === 'CANCELLED', attempt: attempt.status === 'CANCELLED' ? null : attempt }; return copy(attempt);
      },
      finish: async payload => {
        if (!tap.attempt || tap.attempt.attemptId !== payload.attemptId) throw new Error('미리보기 도전을 다시 시작해 주세요.');
        const attempt: TapAttempt = { ...tap.attempt, status: 'VALID', tapCount: payload.taps.length, provisional: false }; tap = { officialAvailable: false, attempt }; return copy(attempt);
      },
    },
    content: {
      own: async kind => copy(slots[kind]), feed: async () => ({ items: [], nextCursor: null }),
      submit: async (kind, _editing, _key, input) => {
        if (!input.content.trim() || input.content.length > 200) throw new Error('내용을 1~200자로 입력해 주세요.');
        const previous = slots[kind];
        const revision: Revision = { id: ++serial, itemId: previous.id ?? serial, number: (previous.latestRevision?.number ?? 0) + 1, content: input.content.trim(), templateId: input.templateId, style: input.style, anonymous: kind === 'wishes' || input.anonymous, submittedAt: new Date().toISOString(), status: 'REVIEW_REQUIRED', reason: 'ADMIN_PREVIEW' };
        slots[kind] = { ...previous, id: revision.itemId, version: previous.version + 1, visibility: 'VISIBLE', pendingRevision: revision, latestRevision: revision }; return copy(slots[kind]);
      },
    },
  };
  function moderate(kind: ContentKind, decision: 'APPROVED' | 'REJECTED') {
    const slot = slots[kind]; if (!slot.pendingRevision) return;
    const revision: Revision = { ...slot.pendingRevision, status: decision, reason: 'ADMIN_PREVIEW' };
    slots[kind] = { ...slot, approvedRevision: decision === 'APPROVED' ? revision : slot.approvedRevision, pendingRevision: null, latestRevision: revision, missionCompleted: decision === 'APPROVED' || slot.missionCompleted };
  }
  return { runtime, moderate };
}
