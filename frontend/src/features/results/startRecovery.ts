import type { TapAttempt, TapState } from './api';

/** Reconcile idempotent start replies without inventing the current official slot. */
export async function recoverTapStart(attempt: TapAttempt, reload: () => Promise<TapState>): Promise<TapState> {
  if (attempt.status === 'CANCELLED') return reload();
  return { officialAvailable: false, attempt };
}

/** Synchronous gate: React's pending state alone cannot stop same-tick double clicks. */
export function createTapStartGate() {
  let inFlight = false;
  return async (task: () => Promise<void>) => {
    if (inFlight) return false;
    inFlight = true;
    try { await task(); return true; }
    finally { inFlight = false; }
  };
}
