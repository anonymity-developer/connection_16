import test from 'node:test';
import assert from 'node:assert/strict';
import { routeScreen, VisitTracker } from '../src/features/analytics/catalog.ts';

test('all routes normalize sensitive values and exclude admin paths', () => {
  const examples = {
    '/': 'HOME', '/missions': 'MISSION_OVERVIEW', '/missions/show-me': 'SHOW_ME', '/me/profile': 'PROFILE',
    '/missions/sync-me': 'SYNC_ME', '/missions/make-a-wish': 'WISH', '/missions/fast-tap': 'FAST_TAP', '/missions/say-something': 'CHEER',
    '/discovery': 'DISCOVERY_RECOMMENDED', '/discovery?view=people&q=private': 'DISCOVERY_PEOPLE', '/discovery?seed=private': 'DISCOVERY_RECOMMENDED',
    '/discovery/graph': 'DISCOVERY_GRAPH', '/discovery/profiles/123?private=secret': 'PROFILE_DETAIL', '/t/private-token': 'NFC_TAG',
    '/auth': 'LOGIN', '/auth/signup': 'SIGNUP', '/auth/password-reset': 'PASSWORD_RESET', '/me/password': 'PASSWORD_CHANGE', '/me': 'ACCOUNT',
    '/unknown?token=private': 'NOT_FOUND', '/complete': 'NOT_FOUND', '/admin': null, '/admin/events': null, '/admin/preview': null,
  };
  for (const [path, expected] of Object.entries(examples)) assert.equal(routeScreen(path, false), expected, path);
  assert.equal(routeScreen('/complete', true), 'COLLECTION_COMPLETE');
});
test('duplicate paints keep one event; navigation, back and real step reentry get fresh IDs', () => {
  const events = [], element = {}, tracker = new VisitTracker(event => events.push(event));
  tracker.observe('user:session', 0, 'FAST_TAP', [{ element, step: 'READY' }]);
  tracker.observe('user:session', 0, 'FAST_TAP', [{ element, step: 'READY' }]);
  assert.equal(events.length, 2);
  tracker.observe('user:session', 0, 'FAST_TAP', [{ element, step: 'PLAYING' }]);
  tracker.observe('user:session', 0, 'FAST_TAP', [{ element, step: 'READY' }]);
  assert.deepEqual(events.filter(e => e.step).map(e => e.step), ['READY', 'PLAYING', 'READY']);
  tracker.observe('user:session', 1, 'HOME', []); tracker.observe('user:session', 2, 'FAST_TAP', [{ element, step: 'READY' }]);
  const pages = events.filter(e => e.type === 'PAGE_VIEW');
  assert.equal(pages.length, 3); assert.equal(pages[1].parentPageKey, pages[0].eventKey); assert.equal(pages[2].parentPageKey, pages[1].eventKey);
  assert.equal(new Set(events.map(e => e.eventKey)).size, events.length);
});
test('session changes, fresh documents and logouts break the chain', () => {
  const events = [], tracker = new VisitTracker(event => events.push(event));
  tracker.observe('1:1', 0, 'HOME', []); tracker.observe('1:2', 0, 'HOME', []);
  tracker.reset(); tracker.observe('2:2', 0, 'HOME', []);
  new VisitTracker(event => events.push(event)).observe('2:2', 0, 'HOME', []);
  assert.equal(events.length, 4); assert.ok(events.every(e => e.parentPageKey === undefined));
});
test('bounded memory collector isolates sender and UUID failures from delivery', () => {
  const tracker = new VisitTracker(() => { throw new Error('offline'); });
  assert.doesNotThrow(() => tracker.observe('1', 0, 'HOME', []));
  const unsupported = new VisitTracker(() => assert.fail('no UUID, no event'), () => { throw new Error('unsupported'); });
  assert.doesNotThrow(() => unsupported.observe('1', 0, 'HOME', []));
  let count = 0; const bounded = new VisitTracker(() => count++);
  for (let i = 0; i < 5000; i++) bounded.observe('1', i, 'HOME', []);
  assert.equal(count, 4000);
});
