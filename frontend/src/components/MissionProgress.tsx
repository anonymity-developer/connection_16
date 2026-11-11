import { useId } from 'react';
import { LetterSticker } from './LetterSticker';
import { StateBadge } from '../pages/Home';
import type { Mission, MissionCode, ScreenData } from '../features/missions/model';

// Copy reflects existing participant screens only; it cannot unlock or submit a mission.
const hints: Record<MissionCode, string> = {
  SHOW_ME: '한 줄 소개와 관심사 1개 이상, 참여 안내·공개 동의가 필요해요. 행사 기간에 조건을 갖추면 첫 번째 S가 지급돼요.',
  SYNC_ME: '내 취향에 맞는 답을 골라요. 제출 전에는 바꿀 수 있고, 최초 결과는 다시 바뀌지 않아요.',
  MAKE_A_WISH: '소원은 익명으로 공개돼요. 200자 이내로, 한 계정에 한 글을 남길 수 있어요.',
  FAST_TAP: '먼저 연습으로 감을 익혀요. 공식 도전은 1회이며 시작 후 화면 이탈·중단은 기회를 사용한 것으로 기록돼요.',
  SAY_SOMETHING: '응원은 익명 또는 닉네임으로 공개해요. 200자 이내로, 한 계정에 한 글을 남길 수 있어요. 내 카드에는 하트를 누를 수 없어요.',
};

export function MissionProgress({ mission, data, signedIn }: { mission: Mission; data: ScreenData; signedIn: boolean }) {
  const headingId = useId();
  const location = mission.code === 'SHOW_ME' ? 'NFC 태그 없이 프로필에서 진행해요.' : mission.hint.trim() && mission.hint !== `현장에서 ${mission.name} 태그를 찾아주세요.`
    ? mission.hint : '위치는 현장 안내를 확인해 주세요.';
  const state = data.progress[mission.code];
  const known = !!data.serverLoaded || data.preview;
  return <aside className="mission-guide mission-progress-guide" aria-label={`${mission.name} 진행 현황과 힌트`}>
    <section className="mission-progress-summary" aria-labelledby={headingId}>
      <h2 id={headingId}>나의 진행 현황</h2>
      <div className="mission-reward"><LetterSticker code={mission.code} collectionState={known && state === 'COMPLETED' ? 'collected' : 'uncollected'} /><div><p>{data.preview ? '개발용 미리보기 · 실제 기록 없음' : !signedIn ? '로그인 후 내 기록을 확인해요.' : !known ? '참여 기록을 확인하고 있어요.' : state === 'COMPLETED' ? '획득한 글자예요.' : '미션 완료 후 글자를 받아요.'}</p></div></div>
      {known && <StateBadge state={state} missionCode={mission.code} />}
    </section>
    <section className="mission-hint" aria-label="미션 힌트">
      <details><summary>미션 안내</summary><p>{location}</p><p>{hints[mission.code]}</p></details>
    </section>
  </aside>;
}
