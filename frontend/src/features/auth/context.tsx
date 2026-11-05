import { participationApi } from '../participation/api';
import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation } from '../../app/navigation';
import { authApi } from './api';
import type { AuthConfig, SessionUser } from './api';

interface AuthState {
  ready: boolean; config: AuthConfig; user: SessionUser | null; flash: string;
  acceptUser: (user: SessionUser | null) => void;
  setFlash: (message: string) => void;
  rememberReturnPath: (path: string) => void;
  takeReturnPath: () => string;
}
const AuthContext = createContext<AuthState | null>(null);
const unavailable = { available: false, passwordMinLength: 15, passwordMaxLength: 64 };
export function AuthProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const preview = import.meta.env.DEV && new URLSearchParams(location.split('?')[1]).has('preview');
  const [ready, setReady] = useState(false);
  const [checkedLocation, setCheckedLocation] = useState<string | null>(null);
  const [config, setConfig] = useState<AuthConfig>(unavailable);
  const [user, setUser] = useState<SessionUser | null>(null);
  const [pendingTag, setPendingTag] = useState(false);
  const [flash, setFlash] = useState('');
  const returnPath = useRef<string | null>(null);
  const revision = useRef(0);
  useEffect(() => {
    let active = true;
    const requestRevision = ++revision.current;
    if (preview) { setUser(null); setConfig(unavailable); setCheckedLocation(location); setReady(true); return; }
    setReady(false);
    Promise.all([authApi.config().catch(() => unavailable), authApi.me().catch(() => null), participationApi.pending().catch(() => ({ present: false }))]).then(([nextConfig, nextUser, pending]) => {
      if (active && requestRevision === revision.current) { setConfig(nextConfig); setUser(nextUser); setPendingTag(pending.present); setCheckedLocation(location); setReady(true); }
    });
    return () => { active = false; };
  }, [location, preview]);
  useEffect(() => {
    const expire = () => setUser(null);
    window.addEventListener('tagme:unauthenticated', expire);
    return () => window.removeEventListener('tagme:unauthenticated', expire);
  }, []);
  function acceptUser(next: SessionUser | null) { revision.current++; setUser(next); setCheckedLocation(location); setReady(true); }
  function rememberReturnPath(path: string) { if (/^\/t\/[A-Za-z0-9_-]{43}$/.test(path)) returnPath.current = path; }
  function takeReturnPath() { const path = pendingTag || returnPath.current ? '/t/resume' : '/'; returnPath.current = null; return path; }
  return <AuthContext.Provider value={{ ready: ready && checkedLocation === location, config, user, flash, acceptUser, setFlash, rememberReturnPath, takeReturnPath }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('AuthProvider is required');
  return auth;
}
