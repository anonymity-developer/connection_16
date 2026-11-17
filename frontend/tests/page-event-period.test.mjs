import test from 'node:test';
import assert from 'node:assert/strict';
import { eventWindow, eventQuery, captureEventWindow, dailyObservations, dayCollectionAvailable } from '../src/features/analytics/period.ts';

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
test('today ends at the captured instant while a completed past day keeps its boundary', () => {
  const now = Date.parse('2026-11-17T05:13:40.123Z');
  assert.deepEqual(captureEventWindow('2026-11-17', '2026-11-17', now), { from: '2026-11-16T15:00:00.000Z', to: '2026-11-17T05:13:40.123Z' });
  assert.equal(captureEventWindow('2026-11-16', '2026-11-16', now).to, '2026-11-16T15:00:00.000Z');
  assert.equal(captureEventWindow('2026-11-17', '2026-11-18', now), null);
});
test('daily observations fill only missing KST days with zero and respect an exclusive midnight', () => {
  const rows = [{ date: '2026-11-17', views: 3, visitors: 2 }];
  assert.deepEqual(dailyObservations(eventWindow('2026-11-16', '2026-11-17'), rows), [
    { date: '2026-11-16', views: 0, visitors: 0 }, ...rows,
  ]);
  assert.equal(dailyObservations(captureEventWindow('2026-11-17', '2026-11-17', Date.parse('2026-11-17T02:00:00Z')), rows).length, 1);
});
test('a day entirely before collection is unavailable, including the exact KST midnight boundary', () => {
  assert.equal(dayCollectionAvailable('2026-11-16', '2026-11-16T15:00:00Z'), false);
  assert.equal(dayCollectionAvailable('2026-11-17', '2026-11-16T15:00:00Z'), true);
  assert.equal(dayCollectionAvailable('2026-11-16', '2026-11-16T14:59:59.999Z'), true);
});
