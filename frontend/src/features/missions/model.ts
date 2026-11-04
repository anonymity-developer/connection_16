export type MissionCode = 'SHOW_ME' | 'SYNC_ME' | 'MAKE_A_WISH' | 'FAST_TAP' | 'SAY_SOMETHING';
export type MissionState = 'LOCKED' | 'UNLOCKED' | 'PENDING' | 'COMPLETED';
export interface Mission {
  code: MissionCode;
  slug: string;
  letter: string;
  name: string;
  korean: string;
  description: string;
  hint: string;
  color: string;
  icon: 'person' | 'sparkles' | 'coin' | 'bolt' | 'message';
}

export const missions: Mission[] = [
  { code: 'SHOW_ME', slug: 'show-me', letter: 'S', name: 'SHOW ME', korean: '나를 소개해요', description: '작은 소개로 시작하는 첫인사.', hint: '현장에서 SHOW ME 태그를 찾아주세요.', color: 'coral', icon: 'person' },
  { code: 'SYNC_ME', slug: 'sync-me', letter: 'S', name: 'SYNC ME', korean: '우리의 취향 주파수', description: '나와 비슷한 결을 가진 동료를 발견해요.', hint: '현장에서 SYNC ME 태그를 찾아주세요.', color: 'blue', icon: 'sparkles' },
  { code: 'MAKE_A_WISH', slug: 'make-a-wish', letter: 'A', name: 'MAKE A WISH', korean: '소원을 띄워요', description: '우리의 바람이 모이는 작은 호수.', hint: '현장에서 MAKE A WISH 태그를 찾아주세요.', color: 'mint', icon: 'coin' },
  { code: 'FAST_TAP', slug: 'fast-tap', letter: 'F', name: 'FAST TAP', korean: '10초의 몰입', description: '손끝의 에너지를 모두 모아, 탭!', hint: '현장에서 FAST TAP 태그를 찾아주세요.', color: 'yellow', icon: 'bolt' },
  { code: 'SAY_SOMETHING', slug: 'say-something', letter: 'Y', name: 'SAY SOMETHING', korean: '마음을 전해요', description: '누군가의 오늘에 힘이 되는 한마디.', hint: '현장에서 SAY SOMETHING 태그를 찾아주세요.', color: 'lilac', icon: 'message' },
];

export const lockedProgress: Record<MissionCode, MissionState> = {
  SHOW_ME: 'LOCKED', SYNC_ME: 'LOCKED', MAKE_A_WISH: 'LOCKED', FAST_TAP: 'LOCKED', SAY_SOMETHING: 'LOCKED',
};

export const stateLabels: Record<MissionState, string> = {
  LOCKED: '태그를 찾아주세요', UNLOCKED: '참여할 수 있어요', PENDING: '검수 중이에요', COMPLETED: '글자 획득 완료',
};

export interface ScreenData {
  preview: boolean;
  serverLoaded?: boolean;
  nickname: string | null;
  progress: Record<MissionCode, MissionState>;
}

export const publicScreen: ScreenData = { preview: false, nickname: null, progress: lockedProgress };
