import { useSyncExternalStore } from 'react';
import { destinationHref } from './destination';
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
  const target = destinationHref(href, window.location.href, import.meta.env.DEV);
  const destination = new URL(target, window.location.origin);
  if (destination.origin !== window.location.origin) return;
  window.history.pushState(null, '', target);
  window.dispatchEvent(new Event(eventName));
}

export function Link({ href = '/', onClick, ...props }: AnchorHTMLAttributes<HTMLAnchorElement>) {
  const targetHref = destinationHref(href, window.location.href, import.meta.env.DEV);
  const url = new URL(targetHref, window.location.origin);
  function click(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.altKey || event.shiftKey || props.target === '_blank' || props.download !== undefined || url.origin !== window.location.origin) return;
    event.preventDefault();
    navigate(targetHref);
  }
  return <a {...props} href={targetHref} onClick={click} />;
}
