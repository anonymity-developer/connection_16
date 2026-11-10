import test from 'node:test';
import assert from 'node:assert/strict';
import { networkLayout } from '../src/features/discovery/layout.ts';

for (const count of [0, 1, 8, 30]) {
  test(`stable direct relationships and separated label boxes for ${count} peers`, () => {
    const nodes = Array.from({ length: count }, (_, i) => ({ id: i + 2, nickname: '긴이름'.repeat(10) }));
    const edges = nodes.map(p => ({ from: 1, to: p.id, weight: p.id === 2 ? 6 : 1 }));
    edges.push({ from: 2, to: 1, weight: 6 }, { from: 2, to: 1, weight: 6 }, { from: 3, to: 4, weight: 6 });
    for (const width of [240, 280, 320, 340]) {
      const layout = networkLayout({ self: { id: 1 }, nodes, edges }, width);
      assert.deepEqual(layout, networkLayout({ self: { id: 1 }, nodes: [...nodes].reverse(), edges: [...edges].reverse() }, width));
      assert.equal(new Set(layout.map(p => p.person.id)).size, layout.length);
      if (count) { assert.equal(layout[0].person.id, 2); assert.ok(layout[0].incoming && layout[0].outgoing); }
      for (const [i, p] of layout.entries()) {
        assert.ok(Math.min(p.x, 320-p.x, p.y, 320-p.y)*width/320 >= 32);
        for (const other of layout.slice(i+1)) assert.ok(Math.abs(other.x-p.x)*width/320 >= 64 || Math.abs(other.y-p.y)*width/320 >= 52);
      }
    }
  });
}
