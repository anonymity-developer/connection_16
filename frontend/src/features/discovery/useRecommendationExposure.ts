import { useCallback, useEffect, useRef } from 'react';
import type { RefObject } from 'react';
import { discoveryApi } from './api';

export function useRecommendationExposure(batchId: string | null, list: RefObject<HTMLUListElement | null>) {
  const pending = useRef(new Set<number>()), sent = useRef(new Set<number>());
  const inflight = useRef<Promise<void>>(Promise.resolve());
  const flush = useCallback(() => {
    const ids = [...pending.current];
    pending.current.clear();
    if (!batchId || !ids.length) return inflight.current;
    // One attempt per card/batch. Retrying a failed write requires a new explicit recommendation request.
    const request = discoveryApi.expose(batchId, ids).catch(() => {});
    inflight.current = Promise.all([inflight.current, request]).then(() => {});
    return inflight.current;
  }, [batchId]);
  useEffect(() => {
    pending.current.clear(); sent.current.clear();
    if (!batchId || !list.current) return;
    let timer: ReturnType<typeof setTimeout> | undefined, active = true;
    const observer = new IntersectionObserver(entries => {
      if (!active) return;
      for (const entry of entries) {
        const id = Number((entry.target as HTMLElement).dataset.profileId);
        if (!entry.isIntersecting || entry.intersectionRatio < .5 || !Number.isSafeInteger(id) || sent.current.has(id)) continue;
        sent.current.add(id); pending.current.add(id); observer.unobserve(entry.target);
      }
      if (pending.current.size && timer === undefined) timer = setTimeout(() => { timer = undefined; void flush(); }, 100);
    }, { threshold: .5 });
    list.current.querySelectorAll('[data-profile-id]').forEach(element => observer.observe(element));
    const leaving = () => { void flush(); };
    window.addEventListener('pagehide', leaving);
    return () => { active = false; observer.disconnect(); clearTimeout(timer); window.removeEventListener('pagehide', leaving); };
  }, [batchId, list, flush]);
  return flush;
}
