import { request } from '../auth/api';
import { eventQuery } from '../analytics/period';
import type { EventWindow } from '../analytics/period';

export interface Screen { id: string; name: string; mission: string | null }
export interface PageEvent {
  ref: string; eventAt: string; recordedAt: string; screenId: string; screenName: string;
  eventType: string; mission: string | null; step: string | null; participantRef: string; sessionRef: string | null; source: string;
}
export interface EventLog { window: EventWindow; items: PageEvent[]; total: number; offset: number; nextOffset: number | null }
export interface EventSummary {
  window: EventWindow; collectionStartedAt: string; syncCollectionStartedAt: string; droppedCompletions: number; screens: Screen[];
  pages: { screenId: string; name: string; views: number; visitors: number; repeatVisitors: number }[];
  days: { date: string; views: number; visitors: number }[];
  flows: { fromScreen: string; toScreen: string; moves: number; users: number }[];
  funnels: { mission: string; stages: { name: string; users: number; entryConversion: number | null; previousConversion: number | null; estimatedDropOff: number | null }[] }[];
}
export const pageEventsApi = {
  log: (window: EventWindow, screen = '', type = '', offset = 0) => request<EventLog>(`/admin/events?${eventQuery(window, screen, type, offset)}`),
  summary: (window: EventWindow) => request<EventSummary>(`/admin/events/summary?${new URLSearchParams({ ...window })}`),
};
