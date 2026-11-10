import type { Collection, EventState } from './api';
// The reconciliation is a CSRF-protected write, never a GET side effect.
export async function loadParticipation(api: {
  collection: () => Promise<Collection>; event: () => Promise<EventState>;
  reconcileShowMe: () => Promise<{ newlyCompleted: boolean }>;
}, current: () => boolean) {
  const [initial, event] = await Promise.all([api.collection(), api.event()]);
  let collection = initial;
  if (current() && event.status === 'OPEN' && collection.missions.some(m => m.code === 'SHOW_ME' && m.active && m.state !== 'COMPLETED')) {
    await api.reconcileShowMe();
    // Another tab/refresh may have won the grant: false must not retain the earlier LOCKED snapshot.
    if (current()) collection = await api.collection();
  }
  return { collection, event };
}
