import { missions } from '../missions/model';
import type { MissionCode } from '../missions/model';

const finished = 'complete';
type Celebration = MissionCode | typeof finished;
const markers: Celebration[] = [...missions.map(mission => mission.code), finished];

// Keep presentation acknowledgements separate from the server's collection record.
export function createCollectionCelebrations(userId?: number) {
  const baseKey = userId === undefined ? null : `tagme:collection-celebration:${userId}`;
  let key = baseKey, generation = 0;
  let seen: Set<Celebration> | null = null;

  function read() {
    if (!key) return null;
    try {
      const stored = localStorage.getItem(key)?.split(',');
      return stored ? markers.filter(marker => stored.includes(marker)) : null;
    } catch { return null; }
  }

  function save() {
    if (!key || !seen) return;
    try { localStorage.setItem(key, [...seen].join(',')); }
    catch { /* In-memory acknowledgements still work during this visit. */ }
  }

  function mergeStored() {
    for (const marker of read() ?? []) seen?.add(marker);
  }

  return {
    setGeneration(next: number) {
      if (next === generation) return;
      generation = next;
      key = baseKey === null ? null : next === 0 ? baseKey : `${baseKey}:round:${next}`;
      seen = null;
    },
    observe(baseline: readonly MissionCode[], latest = baseline) {
      const stored = read();
      if (!seen) {
        const initial = new Set<Celebration>(stored ?? baseline);
        if (!stored && missions.every(mission => initial.has(mission.code))) initial.add(finished);
        seen = initial;
      }
      const acknowledged = seen;
      for (const marker of stored ?? []) acknowledged.add(marker);
      // Preserve an unseen change if the participant reloads before visiting the board.
      if (latest.some(code => !acknowledged.has(code))) save();
    },
    takeLetters(completed: readonly MissionCode[]) {
      mergeStored();
      const acknowledged = seen;
      if (!acknowledged) return [];
      const earned = completed.filter(code => !acknowledged.has(code));
      for (const code of earned) acknowledged.add(code);
      if (earned.length) save();
      return earned;
    },
    takeCompletion() {
      mergeStored();
      const acknowledged = seen;
      if (!acknowledged || acknowledged.has(finished) || !missions.every(mission => acknowledged.has(mission.code))) return false;
      acknowledged.add(finished);
      save();
      return true;
    },
  };
}

export type CollectionCelebrations = ReturnType<typeof createCollectionCelebrations>;
