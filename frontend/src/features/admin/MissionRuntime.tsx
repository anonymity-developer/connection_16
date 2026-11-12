import { createContext, useContext } from 'react';
import type { resultsApi } from '../results/api';
import type { tapRankingApi } from '../results/api';
import type { contentApi } from '../content/api';
import type { participationApi } from '../participation/api';
import type { reactionsApi } from '../content/reactions-api';

export interface MissionRuntime { results: typeof resultsApi; content: typeof contentApi; reactions: typeof reactionsApi; participation: Pick<typeof participationApi, 'profile' | 'interests' | 'event' | 'saveProfile'>; refresh: () => Promise<void>; showMeCompleted?: boolean; tapRanking?: typeof tapRankingApi }
export const MissionRuntimeContext = createContext<MissionRuntime | null>(null);
export function useMissionRuntime() { return useContext(MissionRuntimeContext); }
