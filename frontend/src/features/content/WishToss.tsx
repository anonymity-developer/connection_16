import type { OwnSlot } from './api';

/** Only fulfilled submission replies enter here. Never reads or changes luck/reward fields. */
export function createWishFeedback() {
  const shown = new Set<number>();
  return (slot: OwnSlot) => {
    const id = slot.latestRevision?.id;
    if (!slot.id || !id || shown.has(id)) return null;
    shown.add(id); return id;
  };
}
export function WishToss({ inLake = false }: { inLake?: boolean }) {
  return <div className={`wish-toss ${inLake ? 'wish-toss--in-lake' : ''}`} role="status">
    <svg viewBox="0 0 320 130" aria-hidden="true" width="320" height="130">
      <ellipse className="toss-lake" cx="190" cy="102" rx="94" ry="20" />
      <g className="toss-coin"><circle cx="70" cy="34" r="12" /><path d="m70 26 2 5 6 1-4 4 1 6-5-3-5 3 1-6-4-4 6-1Z" /></g>
      <ellipse className="toss-ripple ripple-one" cx="190" cy="102" rx="25" ry="6" />
      <ellipse className="toss-ripple ripple-two" cx="190" cy="102" rx="44" ry="10" />
    </svg>
    <p><strong>퐁당! 소원이 접수됐어요.</strong><br />검수 상태는 아래에서 확인해 주세요. A는 첫 검수 승인 후 받아요.</p>
  </div>;
}
