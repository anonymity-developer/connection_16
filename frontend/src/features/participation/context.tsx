import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/context';
import { useLocation } from '../../app/navigation';
import { loadParticipation } from './refresh';
import { participationApi } from './api';
import type { Collection, EventState } from './api';
interface ParticipationState { collection: Collection | null; event: EventState | null; loading: boolean; error: string; refresh: () => Promise<void> }
const Context = createContext<ParticipationState | null>(null);
export function ParticipationProvider({ children }: { children: ReactNode }) {
  const auth = useAuth(); const location = useLocation();
  const preview = import.meta.env.DEV && new URLSearchParams(location.split('?')[1]).has('preview');
  const [collection, setCollection] = useState<Collection | null>(null);
  const [collectionOwner, setCollectionOwner] = useState<number | undefined>(undefined);
  const [event, setEvent] = useState<EventState | null>(null);
  const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  const revision = useRef(0); const userId = auth.user?.id;
  const refresh = useCallback(async () => {
    const version = ++revision.current;
    if (preview || !userId) { setCollection(null); setEvent(null); setLoading(false); setError(''); return; }
    setLoading(true); setError('');
    try {
      const { collection: next, event: schedule } = await loadParticipation(participationApi, () => version === revision.current);
      if (version === revision.current) { setCollection(next); setCollectionOwner(userId); setEvent(schedule); }
    } catch (failure) {
      if (version === revision.current) { setCollection(null); setError(failure instanceof Error ? failure.message : '참여 기록을 불러오지 못했어요.'); }
    } finally { if (version === revision.current) setLoading(false); }
  }, [userId, preview]);
  useEffect(() => { void refresh(); return () => { revision.current++; }; }, [refresh, location]);
  // Never show a previous account's collection while a new account loads.
  return <Context.Provider value={{ collection: userId && userId === collectionOwner && !preview ? collection : null, event, loading, error, refresh }}>{children}</Context.Provider>;
}
export function useParticipation() { const value = useContext(Context); if (!value) throw new Error('ParticipationProvider is required'); return value; }
