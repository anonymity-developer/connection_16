export interface EventWindow { from: string; to: string }
export const kstDay = (now = Date.now()) => new Date(now + 9 * 3600000).toISOString().slice(0, 10);
// Inclusive KST calendar dates become an exclusive UTC end. No local-machine timezone assumptions.
export function eventWindow(start: string, end: string): EventWindow | null {
  const parse = (value: string) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return NaN;
    const instant = Date.parse(`${value}T00:00:00+09:00`);
    if (!Number.isFinite(instant) || new Date(instant + 9 * 3600000).toISOString().slice(0, 10) !== value) return NaN;
    return instant;
  };
  const from = parse(start), to = parse(end) + 86400000;
  return Number.isFinite(from) && Number.isFinite(to) && to > from && to - from <= 31 * 86400000 ? { from: new Date(from).toISOString(), to: new Date(to).toISOString() } : null;
}
export function eventQuery(window: EventWindow, screen = '', type = '', offset = 0) {
  const params = new URLSearchParams({ ...window, offset: String(offset), limit: '50' });
  if (screen) params.set('screen', screen); if (type) params.set('type', type);
  return params.toString();
}
export function captureEventWindow(start: string, end: string, now = Date.now()): EventWindow | null {
  const window = eventWindow(start, end);
  if (!window || end > kstDay(now)) return null;
  const to = Math.min(Date.parse(window.to), now);
  return Date.parse(window.from) < to ? { from: window.from, to: new Date(to).toISOString() } : null;
}
export function dailyObservations(window: EventWindow, rows: { date: string; views: number; visitors: number }[]) {
  const observed = new Map(rows.map(row => [row.date, row])), days = [];
  const start = Date.parse(`${kstDay(Date.parse(window.from))}T00:00:00+09:00`);
  for (let day = start; day < Date.parse(window.to) && days.length < 31; day += 86400000) {
    const date = kstDay(day); days.push(observed.get(date) ?? { date, views: 0, visitors: 0 });
  }
  return days;
}
export function dayCollectionAvailable(date: string, startedAt: string) {
  return Date.parse(`${date}T00:00:00+09:00`) + 86400000 > Date.parse(startedAt);
}
