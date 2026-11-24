// Normalize locally. No URL, query value, profile id or NFC token crosses the API.
export function routeScreen(location: string, complete: boolean) {
  const [raw, search] = location.split('?'), path = raw.replace(/\/$/, '') || '/';
  if (path === '/admin' || path.startsWith('/admin/')) return null;
  const fixed: Record<string, string> = {
    '/': 'HOME', '/missions': 'MISSION_OVERVIEW', '/missions/show-me': 'SHOW_ME',
    '/me/profile': 'PROFILE', '/missions/sync-me': 'SYNC_ME', '/missions/make-a-wish': 'WISH',
    '/missions/fast-tap': 'FAST_TAP', '/missions/say-something': 'CHEER', '/discovery/graph': 'DISCOVERY_GRAPH',
    '/me': 'ACCOUNT', '/auth': 'LOGIN', '/auth/signup': 'SIGNUP', '/auth/password-reset': 'PASSWORD_RESET', '/me/password': 'PASSWORD_CHANGE',
  };
  if (path === '/discovery') return new URLSearchParams(search).get('view') === 'people' ? 'DISCOVERY_PEOPLE' : 'DISCOVERY_RECOMMENDED';
  if (/^\/discovery\/profiles\/\d+$/.test(path)) return 'PROFILE_DETAIL';
  if (path.startsWith('/t/')) return 'NFC_TAG';
  if (path === '/complete') return complete ? 'COLLECTION_COMPLETE' : 'NOT_FOUND';
  return fixed[path] ?? 'NOT_FOUND';
}
export interface Observation { eventKey: string; screen: string; type: 'PAGE_VIEW' | 'MISSION_STEP_VIEW'; step?: string; parentPageKey?: string }
// Per-document memory only. A new visit gets a new UUID; duplicate paints retain it.
export class VisitTracker {
  private identity = ''; private visit = ''; private pageKey: string | undefined;
  private seen = new WeakMap<Element, string>(); private count = 0;
  private send: (event: Observation) => void;
  private uuid: () => string;
  constructor(send: (event: Observation) => void, uuid: () => string = () => crypto.randomUUID()) { this.send = send; this.uuid = uuid; }
  reset() { this.identity = ''; this.visit = ''; this.pageKey = undefined; this.seen = new WeakMap(); }
  observe(identity: string, navigation: number, screen: string, steps: { element: Element; step: string }[]) {
    if (this.identity !== identity) { this.reset(); this.identity = identity; }
    const visit = `${navigation}:${screen}`;
    if (visit !== this.visit) {
      let eventKey: string;
      try { eventKey = this.uuid(); } catch { return; }
      this.visit = visit; this.seen = new WeakMap();
      const parentPageKey = this.pageKey; this.pageKey = eventKey;
      this.emit({ eventKey, screen, type: 'PAGE_VIEW', parentPageKey });
    }
    for (const { element, step } of steps) {
      if (this.seen.get(element) === step) continue;
      let eventKey: string;
      try { eventKey = this.uuid(); } catch { return; }
      this.seen.set(element, step);
      this.emit({ eventKey, screen, type: 'MISSION_STEP_VIEW', step, parentPageKey: this.pageKey });
    }
  }
  private emit(event: Observation) { if (this.count++ < 4000) { try { this.send(event); } catch { /* Isolated from rendering. */ } } }
}
