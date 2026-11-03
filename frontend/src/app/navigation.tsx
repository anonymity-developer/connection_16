import { useSyncExternalStore } from 'react';
import type { AnchorHTMLAttributes, MouseEvent } from 'react';

const eventName = 'tagme:navigation';
const snapshot = () => window.location.pathname + window.location.search;

function subscribe(listener: () => void) {
  window.addEventListener('popstate', listener);
  window.addEventListener(eventName, listener);
  return () => {
    window.removeEventListener('popstate', listener);
    window.removeEventListener(eventName, listener);
  };
}

export function useLocation() {
  return useSyncExternalStore(subscribe, snapshot);
}

export function navigate(href: string) {
  const destination = new URL(href, window.location.origin);
  if (destination.origin !== window.location.origin) return;
  // Keep an explicitly requested local UI preview when changing screens.
  if (import.meta.env.DEV && new URLSearchParams(window.location.search).has('preview')) {
    destination.searchParams.set('preview', new URLSearchParams(window.location.search).get('preview') ?? 'locked');
  }
  window.history.pushState(null, '', destination.pathname + destination.search + destination.hash);
  window.dispatchEvent(new Event(eventName));
}

export function Link({ href = '/', onClick, ...props }: AnchorHTMLAttributes<HTMLAnchorElement>) {
  const preview = import.meta.env.DEV ? new URLSearchParams(window.location.search).get('preview') : null;
  const url = new URL(href, window.location.origin);
  if (preview !== null && url.origin === window.location.origin) url.searchParams.set('preview', preview);
  const targetHref = url.origin === window.location.origin ? url.pathname + url.search + url.hash : href;
  function click(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey || event.shiftKey || props.target === '_blank' || props.download !== undefined || url.origin !== window.location.origin) return;
    event.preventDefault();
    navigate(targetHref);
  }
  return <a {...props} href={targetHref} onClick={click} />;
}
