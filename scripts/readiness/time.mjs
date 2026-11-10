// MySQL DATETIME(6) UTC precision, without Date.parse's invalid-day normalization.
export function utcMicros(value) {
  if (typeof value !== 'string') return null;
  const m = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,6}))?Z$/.exec(value);
  if (!m || Number(m[1].slice(0, 4)) < 1000) return null;
  const seconds = Date.parse(m[1] + 'Z');
  if (!Number.isFinite(seconds) || new Date(seconds).toISOString().slice(0, 19) !== m[1]) return null;
  return BigInt(seconds) * 1000n + BigInt((m[2] || '').padEnd(6, '0'));
}
export function phase(event, now) {
  const values = [event?.opensAt, event?.submitClosesAt, event?.moderationClosesAt].map(utcMicros);
  const at = utcMicros(now);
  if (at === null || values.some(v => v === null) || !(values[0] < values[1] && values[1] <= values[2])) return 'NOT_CONFIGURED';
  return at < values[0] ? 'BEFORE_OPEN' : at < values[1] ? 'OPEN' : 'CLOSED';
}
