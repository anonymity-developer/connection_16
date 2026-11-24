import { useEffect, useRef } from 'react';
import { useAuth } from '../auth/context';
import { postTelemetry, telemetryRevision } from '../auth/api';
import { useParticipation } from '../participation/context';
import { useLocation } from '../../app/navigation';
import { routeScreen, VisitTracker } from './catalog';

function displayed(element: Element, inViewport = false) {
  if (!(element instanceof HTMLElement) || element.closest('dialog:not([open]),[hidden],[aria-hidden="true"]') || !element.getClientRects().length) return false;
  const style = getComputedStyle(element), rect = element.getBoundingClientRect();
  return style.visibility !== 'hidden' && style.display !== 'none' && (!inViewport || rect.bottom > 0 && rect.top < innerHeight && rect.right > 0 && rect.left < innerWidth);
}
export function PageEventObserver() {
  const auth = useAuth(), participation = useParticipation(), location = useLocation();
  const navigation = useRef(0), tracker = useRef<VisitTracker | null>(null);
  if (!tracker.current) tracker.current = new VisitTracker(event => { void postTelemetry('/analytics/events', event); });
  useEffect(() => {
    const moved = () => { navigation.current++; };
    const restored = (event: PageTransitionEvent) => { if (event.persisted) moved(); };
    window.addEventListener('tagme:navigation', moved); window.addEventListener('popstate', moved);
    window.addEventListener('pageshow', restored);
    return () => { window.removeEventListener('tagme:navigation', moved); window.removeEventListener('popstate', moved); window.removeEventListener('pageshow', restored); };
  }, []);
  useEffect(() => {
    const base = routeScreen(location, !!participation.collection?.completion.complete);
    if (import.meta.env.DEV || navigator.webdriver || !auth.ready || !auth.user || auth.user.admin || !base) {
      if (auth.ready && (!auth.user || auth.user.admin) || !base) tracker.current?.reset();
      return;
    }
    let frame = 0;
    const inspect = () => {
      frame = 0;
      if (document.visibilityState !== 'visible') return;
      const root = [...document.querySelectorAll('dialog[open]')].at(-1) ?? document.getElementById('main-content');
      if (!root || !displayed(root)) return;
      const marker = [...root.querySelectorAll('[data-analytics-screen]')].find(element => displayed(element));
      const screen = marker?.getAttribute('data-analytics-screen') || base;
      const steps = [...root.querySelectorAll('[data-analytics-step]')].filter(element => displayed(element, true)).map(element => ({ element, step: element.getAttribute('data-analytics-step')! })).filter(item => !!item.step);
      tracker.current?.observe(`${auth.user!.id}:${telemetryRevision()}`, navigation.current, screen, steps);
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(inspect); };
    const observer = new MutationObserver(schedule);
    observer.observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-analytics-step', 'data-analytics-screen', 'open', 'hidden'] });
    document.addEventListener('visibilitychange', schedule); window.addEventListener('scroll', schedule, true);
    window.addEventListener('tagme:navigation', schedule); window.addEventListener('popstate', schedule);
    window.addEventListener('pageshow', schedule);
    window.addEventListener('resize', schedule); schedule();
    return () => { observer.disconnect(); cancelAnimationFrame(frame); document.removeEventListener('visibilitychange', schedule); window.removeEventListener('scroll', schedule, true); window.removeEventListener('resize', schedule); window.removeEventListener('tagme:navigation', schedule); window.removeEventListener('popstate', schedule); window.removeEventListener('pageshow', schedule); };
  }, [auth.ready, auth.user, location, participation.collection?.completion.complete]);
  return null;
}
