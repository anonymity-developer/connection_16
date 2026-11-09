import { useId } from 'react';
import { LetterSticker } from './LetterSticker';
import { StateBadge } from '../pages/Home';
import type { Mission, MissionCode, ScreenData } from '../features/missions/model';

// Copy reflects existing participant screens only; it cannot unlock or submit a mission.
const hints: Record<MissionCode, string> = {
  SHOW_ME: '한 줄 소개와 관심사 1개 이상을 저장하고 참여 안내를 확인해요. SHOW ME 태그 해금도 필요해요.',
  SYNC_ME: '내 취향에 맞는 답을 골라요. 제출 전에는 바꿀 수 있고, 최초 결과는 다시 바뀌지 않아요.',
  MAKE_A_WISH: '함께 이루고 싶은 작은 바람을 적어봐요. 제출만으로는 획득되지 않고 검수 승인이 필요해요.',
  FAST_TAP: '먼저 연습으로 감을 익혀요. 공식 도전은 1회이며 시작 후 화면 이탈·중단은 기회를 사용한 것으로 기록돼요.',
  SAY_SOMETHING: '동료에게 전할 응원을 적고 공개 방식을 확인해요. 첫 검수 승인 전에는 글자가 지급되지 않아요.',
};

export function MissionProgress({ mission, data, signedIn }: { mission: Mission; data: ScreenData; signedIn: boolean }) {
  const headingId = useId();
  const state = data.progress[mission.code];
  const known = !!data.serverLoaded || data.preview;
  return <aside className="mission-guide mission-progress-guide" aria-label={`${mission.name} 진행 현황과 힌트`}>
    <section className="mission-progress-summary" aria-labelledby={headingId}>
      <h2 id={headingId}>나의 진행 현황</h2>
      <div className="mission-reward"><LetterSticker code={mission.code} collectionState={known && state === 'COMPLETED' ? 'collected' : 'uncollected'} /><div><b>{mission.name}</b><p>{data.preview ? '개발용 미리보기 · 실제 기록 없음' : !signedIn ? '로그인 후 내 기록을 확인해요.' : !known ? '참여 기록을 확인하고 있어요.' : state === 'COMPLETED' ? '서버에 저장된 획득 기록이에요.' : ['MAKE_A_WISH', 'SAY_SOMETHING'].includes(mission.code) ? '검수 승인 후 글자를 받아요.' : '미션 완료 후 글자를 받아요.'}</p></div></div>
      {known && <StateBadge state={state} missionCode={mission.code} />}
    </section>
    <section className="mission-hint" aria-label="미션 힌트">
      <p>다음 한 걸음이 궁금한가요?</p>
      <details><summary>미션 힌트 보기</summary><p>{mission.hint}</p><p>{hints[mission.code]}</p><p>미션 주소만으로는 해금되거나 글자가 지급되지 않아요. 현장의 NFC·QR로 참여해 주세요.</p></details>
    </section>
  </aside>;
}
