import test from 'node:test';
import assert from 'node:assert/strict';
import { relationshipPresentation } from '../src/features/discovery/relationship.ts';
test('graph gracefully handles old APIs and clamps the display of excessive weights', () => {
  assert.equal(relationshipPresentation().label, '프로필 방문 연결');
  assert.equal(relationshipPresentation(NaN).width, 1.5);
  assert.equal(relationshipPresentation(1).width, 1.5);
  assert.equal(relationshipPresentation(3).width, 2.5);
  assert.equal(relationshipPresentation(6).width, 4);
  assert.equal(relationshipPresentation(999).width, 4);
});
