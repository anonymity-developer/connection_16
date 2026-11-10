import type { MissionCode } from '../features/missions/model';

export const letterAssets: Record<MissionCode, string> = {
  SHOW_ME: '/images/letters/show-me.webp', SYNC_ME: '/images/letters/sync-me.webp',
  MAKE_A_WISH: '/images/letters/make-a-wish.webp', FAST_TAP: '/images/letters/fast-tap.webp',
  SAY_SOMETHING: '/images/letters/say-something.webp',
};
/** Artwork is decorative: the parent retains the mission/letter/state accessible name.
 * A colored mission illustration is not proof of a collected reward. */
export function LetterSticker({ code, collectionState, celebrate = false }: {
  code: MissionCode; collectionState?: 'uncollected' | 'collected'; celebrate?: boolean;
}) {
  const collected = collectionState === 'collected';
  return <span className={`letter-sticker ${collectionState ? collected ? 'is-collected' : 'is-uncollected' : ''} ${collected && celebrate ? 'celebrate' : ''}`} aria-hidden="true">
    <img src={letterAssets[code]} alt="" width={320} height={400} draggable={false} decoding="async" />
    {collected && <svg className="letter-sparkle" width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><path d="M10 0 13 7 20 10 13 13 10 20 7 13 0 10 7 7Z" /></svg>}
  </span>;
}
