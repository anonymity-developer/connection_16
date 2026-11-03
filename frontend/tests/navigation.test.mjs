import test from 'node:test';
import assert from 'node:assert/strict';
import { destinationHref } from '../src/app/destination.ts';

const current = 'https://tagme.example.test/?preview=locked';

test('preview switcher can explicitly change its current mode', () => {
  assert.equal(destinationHref('/?preview=pending', current, true), '/?preview=pending');
  assert.equal(destinationHref('/?preview=complete', current, true), '/?preview=complete');
});

test('development navigation carries preview without dropping query or hash', () => {
  assert.equal(destinationHref('/missions/make-a-wish?view=list#form', current, true), '/missions/make-a-wish?view=list&preview=locked#form');
  assert.equal(destinationHref('/auth', 'https://tagme.example.test/', true), '/auth');
});

test('production navigation never propagates the preview state', () => {
  assert.equal(destinationHref('/auth', current, false), '/auth');
});

test('external links are never given a local preview parameter', () => {
  const external = 'https://example.org/profile?id=16#about';
  assert.equal(destinationHref(external, current, true), external);
});
