// A preview follows internal navigation only in development. An explicit
// destination mode always wins, so the preview switcher can change modes.
export function destinationHref(href: string, currentHref: string, development: boolean) {
  const current = new URL(currentHref);
  const destination = new URL(href, current.origin);
  if (destination.origin !== current.origin) return href;
  const preview = current.searchParams.get('preview');
  if (development && preview !== null && !destination.searchParams.has('preview')) destination.searchParams.set('preview', preview);
  return destination.pathname + destination.search + destination.hash;
}
