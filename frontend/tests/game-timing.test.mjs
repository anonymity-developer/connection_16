import test from 'node:test';
import assert from 'node:assert/strict';
import { gameWindow, localPhase } from '../src/features/results/timing.ts';
test('server window uses monotonic elapsed time and accounts for half the request round trip', () => {
  const window = gameWindow('2026-01-01T00:00:00Z', '2026-01-01T00:00:05Z', '2026-01-01T00:00:15Z', 1000, 200);
  assert.deepEqual(window, { starts: 5900, ends: 15900 });
  assert.equal(localPhase(5899, window.starts, window.ends), 'COUNTDOWN');
  assert.equal(localPhase(5900, window.starts, window.ends), 'PLAYING');
  assert.equal(localPhase(15900, window.starts, window.ends), 'FINISHED');
});
