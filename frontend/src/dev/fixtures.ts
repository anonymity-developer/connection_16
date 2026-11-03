import { lockedProgress } from '../features/missions/model';
import type { ScreenData } from '../features/missions/model';

// Imported only behind import.meta.env.DEV. Never represents a server session.
export function previewScreen(mode: string): ScreenData {
  const progress = { ...lockedProgress };
  if (mode === 'unlocked') Object.keys(progress).forEach((code) => { progress[code as keyof typeof progress] = 'UNLOCKED'; });
  if (mode === 'pending') Object.assign(progress, { SHOW_ME: 'COMPLETED', SYNC_ME: 'COMPLETED', MAKE_A_WISH: 'PENDING', SAY_SOMETHING: 'UNLOCKED' });
  if (mode === 'complete') Object.keys(progress).forEach((code) => { progress[code as keyof typeof progress] = 'COMPLETED'; });
  return { preview: true, nickname: '느긋한 수달', progress };
}
