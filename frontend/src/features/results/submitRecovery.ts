import { ApiError } from '../auth/api';
import type { TapAttempt, TapPayload } from './api';

/** Retry only the frozen finish payload; never creates a new official attempt. */
export async function submitTapRecord(payload: TapPayload, finish: (body: TapPayload, signal?: AbortSignal) => Promise<TapAttempt>,
  options: { deadline: number; active: () => boolean; now?: () => number; wait?: (ms: number) => Promise<void>; onRetry?: () => void }) {
  const now = options.now ?? (() => performance.now());
  const wait = options.wait ?? (ms => new Promise(resolve => setTimeout(resolve, ms)));
  for (let retry = 0; ; retry++) {
    if (!options.active()) throw new Error('기록 전송 화면이 닫혔어요. 서버에 저장된 상태를 다시 확인해 주세요.');
    const controller = new AbortController();
    // A manual lookup after the grace window may still recover an already committed response.
    const timeout = setTimeout(() => controller.abort(), 8000);
    try { return await finish(payload, controller.signal); }
    catch (failure) {
      const recoverable = failure instanceof ApiError && (failure.status >= 500 || failure.code === 'TAP_TOO_EARLY');
      const delay = 1000 * 2 ** retry;
      if (!recoverable || retry >= 3 || !options.active() || now() + delay >= options.deadline) throw failure;
      options.onRetry?.(); await wait(delay);
      if (now() >= options.deadline) throw failure;
    } finally { clearTimeout(timeout); }
  }
}
