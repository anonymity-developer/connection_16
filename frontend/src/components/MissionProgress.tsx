import { useId } from 'react';
import { LetterSticker } from './LetterSticker';
import { StateBadge } from '../pages/Home';
import type { Mission, MissionCode, ScreenData } from '../features/missions/model';

// Copy reflects existing participant screens only; it cannot unlock or submit a mission.
const hints: Record<MissionCode, string> = {
  SHOW_ME: 'NFC 태그 없이 참여할 수 있어요. 회원가입 후 나를 소개하는 프로필을 등록하면 첫 번째 S를 받을 수 있어요. 한 줄 소개 작성, 관심사 1개 이상 선택, 참여 안내·공개 동의가 필요해요.',
  SYNC_ME: '내 취향에 가까운 답을 골라 성향을 알아봐요. 제출하면 결과가 확정되며 다시 참여할 수 없어요. 결과를 바탕으로 나와 비슷한 성향의 동료를 찾아보세요.',
  MAKE_A_WISH: '한 계정에 소원 하나를 400자 이내로 남길 수 있고, 나중에 수정할 수 있어요. 익명 또는 닉네임으로 공개할 수 있어요. 서로를 배려하는 표현을 사용해 주세요. 다른 사람의 소원에 댓글로 응원을 전하다 보면 좋은 일이 생길지도 몰라요.',
  FAST_TAP: '10초 동안 빠르게 버튼을 탭해 최고 기록에 도전해 보세요. 전체 최고 기록을 갱신하면 소감 한마디를 남길 수 있어요. 공개 순위에는 상위 10명만 표시돼요. 공식 도전은 1회이며 시작 후 화면 이탈·중단은 기회를 사용한 것으로 기록돼요.',
  SAY_SOMETHING: '한 계정에 응원 하나를 400자 이내로 남길 수 있고, 나중에 수정할 수 있어요. 익명 또는 닉네임으로 공개할 수 있어요. 서로를 배려하는 표현을 사용해 주세요. 동료의 마음을 움직일 글을 남기고, 내 마음을 움직인 글에는 하트를 눌러보세요.',
};

export function MissionProgress({ mission, data, signedIn }: { mission: Mission; data: ScreenData; signedIn: boolean }) {
  const headingId = useId();
  const location = mission.hint.trim() && mission.hint !== `현장에서 ${mission.name} 태그를 찾아주세요.`
    ? mission.hint : '위치는 현장 안내를 확인해 주세요.';
  const state = data.progress[mission.code];
  const known = !!data.serverLoaded || data.preview;
  return <aside className="mission-guide mission-progress-guide" aria-label={`${mission.name} 진행 현황과 힌트`}>
    <section className="mission-progress-summary" aria-labelledby={headingId}>
      <h2 id={headingId}>나의 진행 현황</h2>
      <div className="mission-reward"><LetterSticker code={mission.code} collectionState={known && state === 'COMPLETED' ? 'collected' : 'uncollected'} /><div className="mission-reward-copy">
        {known && <StateBadge state={state} missionCode={mission.code} />}
        {(data.preview || !signedIn || !known) && <p>{data.preview ? '개발용 미리보기 · 실제 기록 없음' : !signedIn ? '로그인 후 내 기록을 확인해요.' : '참여 기록을 확인하고 있어요.'}</p>}
      </div></div>
    </section>
    <section className="mission-hint" aria-label="미션 힌트">
      <details><summary>미션 안내</summary><p>{location}</p><p>{hints[mission.code]}</p><div className="mission-tip-note"><h3>TIP</h3><p>{mission.encouragement}</p></div></details>
    </section>
  </aside>;
}
