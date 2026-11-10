import type { TapAttempt, TapState } from './api';
import { recoverTapStart } from './startRecovery.ts';
/** An old cancel response describes that attempt, not the user's current official slot. */
export async function cancelTapAttempt(id: number, api: { cancel: (id: number) => Promise<TapAttempt>; tap: () => Promise<TapState> }): Promise<TapState> {
  return recoverTapStart(await api.cancel(id), api.tap);
}
