import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom', logLevel: 'error' });
after(() => server.close());
const { submitTapRecord } = await server.ssrLoadModule('/src/features/results/submitRecovery.ts');
const { ApiError } = await server.ssrLoadModule('/src/features/auth/api.ts');
const payload = { attemptId: 7, taps: [100, 100, 201], interruptions: [] };

test('lost finish response resends exactly the same record without creating another attempt', async () => {
  let calls = 0, clock = 0; const seen = [], waits = [], saved = { attemptId: 7, status: 'VALID', tapCount: 3 };
  const actual = await submitTapRecord(payload, async (body, signal) => {
    seen.push(body); assert.ok(signal instanceof AbortSignal);
    if (++calls === 1) throw new ApiError(503, 'NETWORK_ERROR', 'Lost acknowledgement after commit');
    return saved;
  }, { deadline: 60000, active: () => true, now: () => clock, wait: async ms => { waits.push(ms); clock += ms; } });
  assert.equal(actual, saved); assert.equal(calls, 2); assert.ok(seen.every(body => body === payload)); assert.deepEqual(waits, [1000]);
});
test('automatic finish retries are bounded by deadline, count, current screen and server denial', async () => {
  for (const error of [new ApiError(403, 'MISSION_LOCKED', 'Locked'), new ApiError(409, 'PARTICIPATION_RESET', 'Reset'), new ApiError(429, 'RATE_LIMITED', 'Limited')]) {
    let calls = 0;
    await assert.rejects(submitTapRecord(payload, async () => { calls++; throw error; }, { deadline: 60000, active: () => true, now: () => 0, wait: async () => assert.fail('must not wait') }));
    assert.equal(calls, 1);
  }
  let calls = 0, clock = 0;
  await assert.rejects(submitTapRecord(payload, async () => { calls++; throw new ApiError(503, 'NETWORK_ERROR', 'Offline'); }, { deadline: 60000, active: () => true, now: () => clock, wait: async ms => { clock += ms; } }));
  assert.equal(calls, 4);
  calls = 0;
  await assert.rejects(submitTapRecord(payload, async () => { calls++; throw new ApiError(503, 'NETWORK_ERROR', 'Offline'); }, { deadline: 500, active: () => true, now: () => 0, wait: async () => assert.fail('too late') }));
  assert.equal(calls, 1);
  await assert.rejects(submitTapRecord(payload, async () => assert.fail('closed screen must not send'), { deadline: 60000, active: () => false }));
});
test('clock estimation early finish retries the frozen payload, not an edited tap count', async () => {
  let calls = 0;
  const result = await submitTapRecord(payload, async body => {
    assert.equal(body, payload); if (++calls === 1) throw new ApiError(409, 'TAP_TOO_EARLY', 'Early'); return { status: 'VALID' };
  }, { deadline: 60000, active: () => true, now: () => 0, wait: async () => {} });
  assert.equal(result.status, 'VALID'); assert.equal(calls, 2);
});
