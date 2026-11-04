export function gameWindow(serverNow: string, startsAt: string, endsAt: string, receivedMono: number, roundTripMs = 0) {
  const estimatedServerAtReceive = Date.parse(serverNow) + Math.max(0, roundTripMs) / 2;
  return { starts: receivedMono + Date.parse(startsAt) - estimatedServerAtReceive, ends: receivedMono + Date.parse(endsAt) - estimatedServerAtReceive };
}
export function localPhase(now: number, starts: number, ends: number) { return now < starts ? 'COUNTDOWN' : now < ends ? 'PLAYING' : 'FINISHED'; }
