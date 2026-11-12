import { interestsComplete } from '../participation/catalog';
import type { MissionRuntime } from './MissionRuntime';
import type { ContentKind, OwnSlot, PublicContent, Revision } from '../content/api';
import type { LikeState, OwnComment } from '../content/reactions-api';
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
  let tapMessage: string | null = null, tapMessageKey: string | null = null;
  const tapBoard = () => {
    const attempt=tap.attempt;
    if(!attempt || attempt.status!=='VALID')return {top:[],own:null};
    return {top:[{rank:1,nickname:'관리자 미리보기',tapCount:attempt.tapCount ?? 0,submittedAt:attempt.endsAt,message:tapMessage}],own:{attemptId:attempt.attemptId,rank:1,tapCount:attempt.tapCount ?? 0,recordBreaker:true,canWriteMessage:tapMessage===null,message:tapMessage}};
  };
  const slots: Record<ContentKind, OwnSlot> = { wishes: empty(), cheers: empty() };
  const comments = new Map<number, OwnComment[]>();
  const hearts = new Map<number, LikeState>();
  const submissions = new Map<string, { fingerprint: string; result: OwnSlot }>();
  const examples: Record<ContentKind, PublicContent> = {
    wishes: { id: 10001, content: '관리자 미리보기 예시 소원입니다. 댓글과 분수 화면을 확인해 보세요.', templateId: 'PLAIN', style: 'ROSE', anonymous: true, nickname: null, profileId: null, luckScore: null, landingZone: null },
    cheers: { id: 10002, content: '관리자 미리보기 예시 응원입니다. 하트를 눌러 보세요.', templateId: 'NOTE', style: 'INK', anonymous: true, nickname: null, profileId: null, luckScore: null, landingZone: null },
  };
  let profile: ProfileState = { identity: { realName: '미리보기 예시', region: '서울', classNo: 1, photoUrl: null, ready: true }, profile: null,
    consents: { ready: true, policies: (['PARTICIPATION', 'PROFILE_PUBLICATION'] as const).map(type => ({ type, version: 'admin-preview', title: '관리자 미리보기 안내', body: '참여 화면 확인용 예시입니다. 실제 동의·프로필·완료 기록은 생성하지 않습니다.', agreed: false })) }, profileComplete: false, participationReady: false };
  const runtime: MissionRuntime = {
    refresh: async () => {},
    tapRanking: {
      board: async () => copy(tapBoard()),
      message: async (attemptId,content,key) => {
        if(!tapBoard().own || tap.attempt?.attemptId!==attemptId || !content.trim() || content.length>100)throw new ApiError(400,'INVALID_RECORD_MESSAGE','미리보기 소감을 확인해 주세요.');
        if(tapMessage!==null && (tapMessageKey!==key || tapMessage!==content.trim()))throw new ApiError(409,'RECORD_MESSAGE_SAVED','이미 소감을 남겼어요.');
        tapMessage=content.trim();tapMessageKey=key;return copy(tapBoard());
      },
    },
    showMeCompleted: false,
    participation: {
      profile: async () => copy(profile), interests: async () => copy(interests),
      event: async () => ({ status: 'OPEN', opensAt: null, submitClosesAt: null, profilePreparationEnabled: true }),
      saveProfile: async input => {
        if (!input.nickname.trim() || input.nickname.trim().length > 30 || input.interestIds.length > 10) throw new ApiError(400, 'INVALID_PROFILE', '닉네임과 관심사를 확인해 주세요.');
        if (input.expectedVersion !== (profile.profile?.version ?? 0)) throw new ApiError(409, 'PROFILE_CHANGED', '미리보기 프로필을 다시 불러와 주세요.');
        if (profile.consents.policies.some(policy => !policy.agreed && !input.acceptPolicies.some(value => value.type === policy.type && value.version === policy.version))) throw new ApiError(409, 'CONSENT_REQUIRED', '미리보기 참여 안내를 확인해 주세요.');
        if (!input.bio?.trim() || !interestsComplete(input.interestIds,interests) || (input.bio?.length ?? 0) > 200 || new Set(input.interestIds).size !== input.interestIds.length || input.interestIds.some(id => !interests.some(item => item.id === id))) throw new ApiError(400, 'INVALID_PROFILE', '소개와 관심사를 확인해 주세요.');
        for (const [url, github] of [[input.githubUrl, true], [input.blogUrl, false]] as const) if (url) {
          let valid = false; try { const parsed = new URL(url); valid = parsed.protocol === 'https:' && !parsed.username && !parsed.password && !parsed.hash && (!parsed.port || parsed.port === '443') && ![...url].some(character => character.charCodeAt(0) <= 32 || character.charCodeAt(0) === 127) && (!github || parsed.hostname === 'github.com'); } catch { /* field error below */ }
          if (!valid) throw new ApiError(400, 'INVALID_URL', 'HTTPS 링크를 입력해 주세요. GitHub는 github.com 주소를 사용해 주세요.');
        }
        profile = { ...profile, profile: { ...input, version: (profile.profile?.version ?? 0) + 1 }, profileComplete: true, participationReady: true, consents: { ready: true, policies: profile.consents.policies.map(p => ({ ...p, agreed: true })) } };
        runtime.showMeCompleted ||= !!input.bio?.trim() && input.interestIds.length > 0;
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
        if (['VALID', 'INVALID', 'INTERRUPTED'].includes(tap.attempt.status)) return copy(tap.attempt);
        if (Date.now() < Date.parse(tap.attempt.endsAt)) throw new Error('미리보기 시간이 끝난 뒤 결과를 확인해 주세요.');
        const attempt: TapAttempt = { ...tap.attempt, status: payload.interruptions.length ? 'INTERRUPTED' : payload.taps.some((at, i) => i > 0 && at - payload.taps[i - 1] < 1) ? 'INVALID' : 'VALID', tapCount: payload.taps.length, provisional: false }; tap = { officialAvailable: false, attempt }; return copy(attempt);
      },
    },
    content: {
      own: async kind => copy(slots[kind]), feed: async kind => {
        const slot = slots[kind], approved = slot.approvedRevision;
        const own: PublicContent[] = approved ? [{ id: slot.id!, content: approved.content, templateId: approved.templateId, style: approved.style, anonymous: approved.anonymous, nickname: approved.anonymous ? null : '관리자 미리보기', profileId: null, luckScore: null, landingZone: null }] : [];
        return { items: copy([...own, examples[kind]]), nextCursor: null };
      },
      submit: async (kind, editing, key, input) => {
        const requestKey = `${kind}:${key}`, fingerprint = JSON.stringify([editing, input]), saved = submissions.get(requestKey);
        if (saved) { if (saved.fingerprint !== fingerprint) throw new ApiError(409, 'REQUEST_CHANGED', '같은 요청의 내용을 바꿀 수 없어요.'); return copy(saved.result); }
        if (!input.content.trim() || input.content.length > 400) throw new Error('내용을 1~400자로 입력해 주세요.');
        const previous = slots[kind];
        if (!!previous.id !== editing || editing && input.expectedVersion !== previous.version) throw new ApiError(409, 'CONTENT_CHANGED', '미리보기 글을 다시 불러와 주세요.');
        const revision: Revision = { id: ++serial, itemId: previous.id ?? serial, number: (previous.latestRevision?.number ?? 0) + 1, content: input.content.trim(), templateId: input.templateId, style: input.style, anonymous: input.anonymous, submittedAt: new Date().toISOString(), status: 'APPROVED', reason: null };
        slots[kind] = { ...previous, id: revision.itemId, version: previous.version + 1, visibility: 'VISIBLE', approvedRevision: revision, pendingRevision: null, latestRevision: revision, missionCompleted: true };
        submissions.set(requestKey, { fingerprint, result: copy(slots[kind]) }); return copy(slots[kind]);
      },
    },
    reactions: {
      hearts: async ids => Object.fromEntries(ids.map(id => [id, copy(hearts.get(id) ?? { liked: false, count: 0, canLike: true })])),
      heart: async id => copy(hearts.get(id) ?? { liked: false, count: 0, canLike: true }),
      setHeart: async (id, liked) => { const state = { liked, count: liked ? 1 : 0, canLike: true }; hearts.set(id, state); return copy(state); },
      comments: async id => ({ items: (comments.get(id) ?? []).filter(c => c.status === 'APPROVED').map(c => ({ id: c.id, content: c.content, revealProfile: c.revealProfile, nickname: c.revealProfile ? '관리자 미리보기' : null, profileId: null })), nextCursor: null }),
      own: async id => ({ items: copy(comments.get(id) ?? []), nextCursor: null }),
      submit: async (id, key, content, revealProfile) => {
        const rows = comments.get(id) ?? [], previous = rows.find(c => c.requestKey === key);
        if (previous) return copy(previous);
        if (!content.trim() || content.length > 200) throw new Error('댓글을 1~200자로 입력해 주세요.');
        const row: OwnComment = { id: ++serial, content: content.trim(), revealProfile, requestKey: key, status: 'APPROVED', reason: null, submittedAt: new Date().toISOString() };
        comments.set(id, [row, ...rows]); return copy(row);
      },
    },
  };
  return { runtime };
}
