import { createContext, useContext } from 'react';
import type { resultsApi } from '../results/api';
import type { contentApi } from '../content/api';
import type { participationApi } from '../participation/api';

export interface MissionRuntime { results: typeof resultsApi; content: typeof contentApi; participation: Pick<typeof participationApi, 'profile' | 'interests' | 'event' | 'saveProfile'>; refresh: () => Promise<void> }
export const MissionRuntimeContext = createContext<MissionRuntime | null>(null);
export function useMissionRuntime() { return useContext(MissionRuntimeContext); }
