import { useEffect, useRef } from 'react';
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
export function WishToss({ earned }: { earned: boolean }) {
  const scene = useRef<HTMLDivElement>(null);
  useEffect(() => { scene.current?.scrollIntoView({ block: 'center' }); }, []);
  return <div ref={scene} className="wish-toss wish-toss--in-lake" role="status">
    <span className="toss-coin" aria-hidden="true">A</span>
    <p><strong>동전이 분수 위에 안착했어요!</strong>{earned && <> A를 획득했어요!</>}</p>
  </div>;
}
