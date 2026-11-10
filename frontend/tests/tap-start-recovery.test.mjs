import test from 'node:test';
import assert from 'node:assert/strict';
import { createTapStartGate, recoverTapStart } from '../src/features/results/startRecovery.ts';
const attempt = status => ({ attemptId: 11, generation: 1, status, serverNow: '2030-01-01T00:00:00Z', startsAt: '2030-01-01T00:00:05Z', endsAt: '2030-01-01T00:00:15Z', submitDeadline: '2030-01-01T00:00:20Z', tapCount: null, reviewReason: null, letterGranted: false });
test('cancelled start replay reloads the actual slot and allows a genuinely unused official attempt', async () => {
  let reads = 0;
  const current = { officialAvailable: true, attempt: null };
  assert.deepEqual(await recoverTapStart(attempt('CANCELLED'), async () => { reads++; return current; }), current);
  assert.equal(reads, 1);
});
test('an old cancelled reply cannot hide a newer attempt from another tab', async () => {
  const current = { officialAvailable: false, attempt: attempt('PLAYING') };
  assert.deepEqual(await recoverTapStart(attempt('CANCELLED'), async () => current), current);
});
test('failed reconciliation propagates rather than marking the official slot as available', async () => {
  await assert.rejects(recoverTapStart(attempt('CANCELLED'), async () => { throw new Error('simulated unavailable'); }), /simulated unavailable/);
});
test('active, completed and non-cancellable attempts retain the server reply without an extra read', async () => {
  for (const status of ['COUNTDOWN', 'PLAYING', 'AWAITING_SUBMISSION', 'VALID', 'REVIEW_REQUIRED', 'EXPIRED', 'INTERRUPTED']) {
    const current = attempt(status);
    const state = await recoverTapStart(current, async () => { assert.fail('must not reload a non-cancelled start'); });
    assert.deepEqual(state, { officialAvailable: false, attempt: current });
  }
});


test('same-tick duplicate starts share one in-flight operation until its late reply finishes', async () => {
  const gate = createTapStartGate(); let release; let calls=0;
  const first = gate(async()=>{calls++;await new Promise(resolve=>{release=resolve;});});
  assert.equal(await gate(async()=>{calls++;}),false);
  assert.equal(calls,1);release();assert.equal(await first,true);
  assert.equal(await gate(async()=>{calls++;}),true);assert.equal(calls,2);
});
test('a failed start releases the gate for a deliberate retry', async () => {
  const gate=createTapStartGate();
  await assert.rejects(gate(async()=>{throw new Error('fixture failure');}),/fixture failure/);
  assert.equal(await gate(async()=>{}),true);
});
