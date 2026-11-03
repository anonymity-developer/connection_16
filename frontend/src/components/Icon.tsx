import type { CSSProperties, ReactNode } from 'react';

export type IconName = 'arrow' | 'arrowLeft' | 'lock' | 'check' | 'person' | 'sparkles' | 'coin' | 'bolt' | 'message' | 'home' | 'people' | 'close' | 'eye' | 'eyeOff' | 'tag' | 'heart' | 'clock';

const paths: Record<IconName, ReactNode> = {
  arrow: <><path d="M5 12h14M13 6l6 6-6 6" /></>,
  arrowLeft: <path d="M19 12H5m6-6-6 6 6 6" />,
  lock: <><rect x="5" y="10" width="14" height="11" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  person: <><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></>,
  sparkles: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z" /><path d="M20 2v4M18 4h4" /></>,
  coin: <><ellipse cx="12" cy="9" rx="8" ry="5" /><path d="M4 9v6c0 2.8 3.6 5 8 5s8-2.2 8-5V9M12 6v6M10 7h3.5a1 1 0 0 1 0 2H11a1 1 0 0 0 0 2h3" /></>,
  bolt: <path d="m14 2-9 12h6l-1 8 9-12h-6l1-8Z" />,
  message: <><path d="M21 11a9 9 0 0 1-9 9H4l-2 2v-9A9 9 0 1 1 21 11Z" /><path d="M7 9h8M7 13h5" /></>,
  home: <><path d="m3 10 9-7 9 7v11h-6v-7H9v7H3V10Z" /></>,
  people: <><circle cx="9" cy="8" r="3" /><path d="M2 21v-2a7 7 0 0 1 14 0v2M16 5a3 3 0 0 1 0 6M22 21v-2a7 7 0 0 0-4-6" /></>,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  eye: <><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
  eyeOff: <><path d="m3 3 18 18M10 5a12 12 0 0 1 12 7 17 17 0 0 1-4 5M6 6a16 16 0 0 0-4 6s3 7 10 7a12 12 0 0 0 4-1" /></>,
  tag: <><path d="M3 3h9l9 9-9 9-9-9V3Z" /><circle cx="8" cy="8" r="1" /></>,
  heart: <path d="M20 5a5 5 0 0 0-8 1 5 5 0 0 0-8-1c-4 4 1 9 8 15 7-6 12-11 8-15Z" />,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
};

export function Icon({ name, size = 20, style }: { name: IconName; size?: number; style?: CSSProperties }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={style}>{paths[name]}</svg>;
}
