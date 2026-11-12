import type { Graph } from './api';

// Reserve an 80px label box and 44px touch target at every point on the ring.
export function networkLayout(graph: Graph, width: number) {
  if (!Number.isFinite(width) || width < 128) return [];
  const neighbors = new Map<number, { outgoing: boolean; incoming: boolean; weight: number }>();
  for (const edge of graph.edges) {
    const outgoing = edge.from === graph.self.id;
    if (!outgoing && edge.to !== graph.self.id) continue;
    const id = outgoing ? edge.to : edge.from;
    if (id === graph.self.id) continue;
    const relation = neighbors.get(id) ?? { incoming: false, outgoing: false, weight: 0 };
    relation.outgoing ||= outgoing;
    relation.incoming ||= !outgoing;
    const weight = edge.weight;
    if (weight !== undefined && Number.isInteger(weight) && weight >= 1 && weight <= 6) {
      relation.weight = Math.max(relation.weight, weight);
    }
    neighbors.set(id, relation);
  }
  const peers = graph.nodes.flatMap(person => {
    const relation = neighbors.get(person.id);
    if (!relation) return [];
    neighbors.delete(person.id);
    return [{ person, ...relation }];
  }).sort((a, b) => b.weight - a.weight || b.person.id - a.person.id);
  const radius = Math.min(124, 160 - 34 * 320 / width);
  // A chord longer than the touch box diagonal separates adjacent labels.
  const capacity = Math.max(1, Math.floor(Math.PI / Math.asin(Math.min(1, 50 / (radius * width / 320)))));
  const drawn = peers.slice(0, capacity);
  return drawn.map((peer, i) => {
    const angle = -Math.PI / 2 + i * 2 * Math.PI / drawn.length;
    return { ...peer, x: 160 + radius * Math.cos(angle), y: 160 + radius * Math.sin(angle) };
  });
}
