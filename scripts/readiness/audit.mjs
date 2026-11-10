import { utcMicros } from './time.mjs';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function audit(snapshot, now = new Date()) {
  const checks = [];
  const add = (id, ok, message) => checks.push({ id, ok, message });
  const event = snapshot.event;
  add('event-row', !!event, 'event_settings id=1 필요');
  if (event) {
    const dates = [event.opensAt, event.submitClosesAt, event.moderationClosesAt].map(utcMicros);
    const at = utcMicros(now.toISOString());
    const ordered = dates.every(d => d !== null) && dates[0] < dates[1] && dates[1] <= dates[2];
    add('event-times', ordered, 'UTC 일정: 시작 < 제출 마감 <= 검수 마감');
    add('event-numbers', Number.isInteger(event.gameDuration) && event.gameDuration === 10 && Number.isInteger(event.settingsVersion) && event.settingsVersion > 0, '공식 게임 game_duration=10, settings_version 양의 정수');
    if (ordered) add('event-open', at >= dates[0] && at < dates[1], '미션 테스트에는 OPEN 필요. 사전 프로필 허용은 미션 해금 허용과 다름');
  }
  const policies = Array.isArray(snapshot.policies) ? snapshot.policies : [];
  add('policy-pair', policies.length === 2 && ['PARTICIPATION', 'PROFILE_PUBLICATION'].every(type => policies.filter(p => p.type === type).length === 1), '현재 정책 두 종류가 각각 하나씩 연결되어야 함');
  add('policy-content', policies.length === 2 && policies.every(p => typeof p.version === 'string' && /^[A-Za-z0-9._-]{1,64}$/.test(p.version) && Number.isInteger(p.titleChars) && p.titleChars > 0 && Number.isInteger(p.bodyChars) && p.bodyChars > 0), '버전 형식·공백 제외 제목/본문 필요. 문구의 승인/적법성을 자동 판정하지 않음');
  add('missions', snapshot.activeMissions === 5, '활성 미션 5개 필요');
  add('sync-config', snapshot.currentQuestionnaires === 1, 'SYNC 현재 설문 포인터 1개 필요. 문항/유형 JSON 유효성은 별도 검증');
  add('content-config', snapshot.contentSettings === 0 || snapshot.contentSettings === 1, '소원 운 점수·구역은 선택 설정. 0이면 미추첨이며, 뒤늦은 설정으로 기존 슬롯을 자동 추첨하지 않음');
  return { readyForMissionSmoke: checks.every(c => c.ok), checks };
}
export function localBase(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('Only an HTTP 127.0.0.1 origin is accepted');
  return url.origin;
}
export async function probe(value, send = fetch) {
  const base = localBase(value);
  const output = [];
  for (const path of ['/api/v1/event', '/api/v1/auth/config', '/actuator/health']) {
    // No credentials, cookies, redirects, OTP, profile or mutation endpoint.
    const response = await send(base + path, { method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(5000) });
    const body = await response.json();
    output.push({ endpoint: path, httpStatus: response.status, ...(path.endsWith('/event') ? { status: ['BEFORE_OPEN','OPEN','CLOSED','NOT_CONFIGURED'].includes(body.status) ? body.status : 'UNKNOWN' } : path.endsWith('/config') ? { available: body.available === true } : { status: ['UP','DOWN','OUT_OF_SERVICE','UNKNOWN'].includes(body.status) ? body.status : 'UNKNOWN' }) });
  }
  return output;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const [mode, value, extra] = process.argv.slice(2);
    if (extra || !value || !['--snapshot', '--probe'].includes(mode)) throw new Error('Usage: node scripts/readiness/audit.mjs --snapshot report.json | --probe http://127.0.0.1:8080');
    if (mode === '--probe') console.log(JSON.stringify(await probe(value), null, 2));
    else { const report = audit(JSON.parse((await readFile(value, 'utf8')).replace(/^\uFEFF/, ''))); console.log(JSON.stringify(report, null, 2)); if (!report.readyForMissionSmoke) process.exitCode = 2; }
  } catch { console.error('Readiness check failed. Check the input format or local endpoint; response bodies and credentials are not printed.'); process.exitCode = 1; }
}
