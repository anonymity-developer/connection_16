import { useEffect, useRef } from 'react';
import type { OwnSlot } from './api';

/** Only fulfilled submission replies enter here. Never reads or changes reward fields. */
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
  useEffect(() => { scene.current?.scrollIntoView({ block: 'center', behavior: 'smooth' }); }, []);
  return <div ref={scene} className="wish-launch" role="status" data-completed={earned}>
    <span className="wish-folding-paper" aria-hidden="true"><i /><i /><i /></span>
    <p><strong>소원을 담은 종이배를 띄웠어요.</strong></p>
  </div>;
}
