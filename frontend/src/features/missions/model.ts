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
  encouragement: string;
  color: string;
  icon: 'person' | 'sparkles' | 'coin' | 'bolt' | 'message';
}

export const missions: Mission[] = [
  { code: 'SHOW_ME', slug: 'show-me', letter: 'S', name: 'SHOW ME', korean: '나를 소개해요', description: '나를 소개하는 프로필을 완성해요!', encouragement: '새로운 인연의 시작이 될 수도 있어.', hint: '프로필을 완성하면 첫 번째 S를 받아요!', color: 'coral', icon: 'person' },
  { code: 'SYNC_ME', slug: 'sync-me', letter: 'S', name: 'SYNC ME', korean: '우리의 취향 주파수', description: '나와 잘 맞는 동료를 찾아봐요!', encouragement: '싱크가 맞는 동료들의 프로필을 탐색해봐!', hint: '현장에서 SYNC ME 태그를 찾아주세요.', color: 'blue', icon: 'sparkles' },
  { code: 'MAKE_A_WISH', slug: 'make-a-wish', letter: 'A', name: 'A WISH COME TRUE', korean: '소원을 띄워요', description: '나의 소원을 남기고, 다른 소원을 댓글로 응원해봐요!', encouragement: '동료의 소원을 응원하면 좋은 일이 생길지도 몰라.', hint: '현장에서 A WISH COME TRUE 태그를 찾아주세요.', color: 'mint', icon: 'coin' },
  { code: 'FAST_TAP', slug: 'fast-tap', letter: 'F', name: 'FAST TAP', korean: '10초의 몰입', description: '10초 동안 최고 탭 기록에 도전해요!', encouragement: '기록은 깨지기 위해 존재한다!', hint: '현장에서 FAST TAP 태그를 찾아주세요.', color: 'yellow', icon: 'bolt' },
  { code: 'SAY_SOMETHING', slug: 'say-something', letter: 'Y', name: 'YOU GOT THIS', korean: '마음을 전해요', description: 'SSAFY 16기 모두를 향한 응원의 메시지를 남겨요!', encouragement: '많은 마음을 움직인 한마디는 특별하니까.', hint: '현장에서 YOU GOT THIS 태그를 찾아주세요.', color: 'lilac', icon: 'message' },
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
