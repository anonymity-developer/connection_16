import test from 'node:test';
import assert from 'node:assert/strict';
import { eventWindow, eventQuery } from '../src/features/analytics/period.ts';

test('KST inclusive dates become fixed half-open UTC period and include the last day', () => {
  const period = eventWindow('2026-11-17', '2026-11-17');
  assert.deepEqual(period, { from: '2026-11-16T15:00:00.000Z', to: '2026-11-17T15:00:00.000Z' });
  const query = new URLSearchParams(eventQuery(period, 'SYNC_ME', 'MISSION_STEP_VIEW', 50));
  assert.equal(query.get('from'), period.from); assert.equal(query.get('to'), period.to);
  assert.equal(query.get('screen'), 'SYNC_ME'); assert.equal(query.get('offset'), '50');
});
test('invalid calendar days, backwards ranges and more than 31 days are rejected', () => {
  for (const [from, to] of [['2026-02-29', '2026-03-01'], ['2026-11-31', '2026-12-01'], ['', '2026-11-17'], ['2026-11-18', '2026-11-17'], ['2026-01-01', '2026-02-01']]) assert.equal(eventWindow(from, to), null);
  assert.ok(eventWindow('2026-01-01', '2026-01-31'));
  assert.ok(eventWindow('2028-02-29', '2028-02-29'));
});
