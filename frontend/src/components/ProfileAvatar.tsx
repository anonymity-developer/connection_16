import { useState } from 'react';
// Only same-origin, server-generated photo routes; never a Mattermost URL or external ID.
export function ProfileAvatar({ name, src, className = '' }: { name: string; src?: string | null; className?: string }) {
  const [failed, setFailed] = useState(false);
  const safe = src && /^\/api\/v1\/(?:users\/me\/photo|discovery\/profiles\/[1-9]\d*\/photo)(?:\?v=\d+)?$/.test(src) ? src : null;
  return <span className={`profile-avatar ${className}`} aria-hidden="true">{safe && !failed ? <img src={safe} alt="" referrerPolicy="no-referrer" onError={() => setFailed(true)} /> : name.match(/[\p{L}\p{N}]/u)?.[0] || 'T'}</span>;
}
