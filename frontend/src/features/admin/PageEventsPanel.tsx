import { useEffect, useState } from 'react';
import { Link, useLocation } from '../../app/navigation';
import { Button } from '../../components/ui';
import { ApiError, request } from '../auth/api';
import { missions } from '../missions/model';
import { captureEventWindow, dailyObservations, dayCollectionAvailable, eventQuery, kstDay } from '../analytics/period';
import type { EventWindow } from '../analytics/period';
import type { EventLog, EventSummary } from './pageEventsApi';
import './page-events.css';

const tabs = [['logs', '이벤트 로그'], ['pages', '페이지 방문'], ['flows', '이동 흐름'], ['funnels', '미션 퍼널']] as const;
const types = { PAGE_VIEW: '페이지 방문', MISSION_STEP_VIEW: '미션 단계', MISSION_SUBMIT_SUCCESS: '서버 저장 완료' };
const number = (value: number) => value.toLocaleString('ko-KR');
const date = (value: string) => new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul' }).format(new Date(value));
const missing = '—';
const rate = (value: number | null) => value === null ? missing : `${value.toFixed(1)}%`;
interface Query { start: string; end: string; window: EventWindow; screen: string; type: string; offset: number }
function initialQuery(): Query {
  const now = Date.now(), start = kstDay(now - 6 * 86400000), end = kstDay(now);
  return { start, end, window: captureEventWindow(start, end, now)!, screen: '', type: '', offset: 0 };
}
function useRead<T>(url: string | null, revision: number, onDenied: (message: string) => void) {
  const [state, setState] = useState<{ url: string | null; value: T | null; busy: boolean; error: boolean }>({ url: null, value: null, busy: true, error: false });
  useEffect(() => {
    if (!url) return;
    let active = true;
    setState(previous => ({ url, value: previous.url === url ? previous.value : null, busy: true, error: false }));
    request<T>(url).then(value => { if (active) setState({ url, value, busy: false, error: false }); }).catch(failure => {
      if (!active) return;
      setState({ url, value: null, busy: false, error: true });
      if (failure instanceof ApiError && (failure.status === 401 || failure.status === 403)) onDenied('관리자 권한을 다시 확인해 주세요.');
    });
    return () => { active = false; };
  }, [url, revision, onDenied]);
  return { data: state.url === url ? state.value : null, busy: !!url && (state.url !== url || state.busy), error: !!url && state.url === url && state.error };
}

export function PageEventsPanel({ onDenied }: { onDenied: (message: string) => void }) {
  const location = useLocation(), selected = new URLSearchParams(location.split('?')[1]).get('tab');
  const tab = tabs.find(([key]) => key === selected)?.[0] ?? 'logs';
  const [query, setQuery] = useState(initialQuery), [revision, setRevision] = useState(0);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [start, setStart] = useState(query.start), [end, setEnd] = useState(query.end);
  const [screen, setScreen] = useState(''), [type, setType] = useState(''), [validation, setValidation] = useState('');
  const summary = useRead<EventSummary>(`/admin/events/summary?${new URLSearchParams({ ...query.window })}`, revision, onDenied);
  const log = useRead<EventLog>(tab === 'logs' ? `/admin/events?${eventQuery(query.window, query.screen, query.type, query.offset)}` : null, revision, onDenied);
  const busy = summary.busy || log.busy, error = summary.error || log.error;
  function refresh() {
    const window = captureEventWindow(query.start, query.end);
    if (!window) return;
    setQuery({ ...query, window, offset: 0 }); setRevision(value => value + 1);
  }
  function apply() {
    const window = captureEventWindow(start, end);
    if (!window) { setValidation('조회 기간은 종료일을 포함해 최대 31일이며, 오늘까지 선택할 수 있어요.'); return; }
    setValidation(''); setQuery({ start, end, window, screen, type, offset: 0 }); setRevision(value => value + 1);
  }
  return <div className="admin-events" aria-busy={busy}>
    <nav className="admin-mission-tabs events-tabs" aria-label="이벤트 분석 보기">{tabs.map(([key, label]) => <Link key={key} href={`/admin/events?tab=${key}`} aria-current={key === tab ? 'page' : undefined}>{label}</Link>)}</nav>
    <Button className="events-filter-toggle" variant="secondary" aria-expanded={filtersOpen} aria-controls="events-filters" onClick={() => setFiltersOpen(value => !value)}>조회 조건</Button>
    <form id="events-filters" className={`events-filters${filtersOpen ? ' is-open' : ''}`} noValidate onSubmit={event => { event.preventDefault(); if (!busy) apply(); }}>
      <div><label htmlFor="events-start">시작일(KST)</label><input id="events-start" type="date" value={start} max={kstDay()} disabled={busy} required aria-describedby="events-period-help" aria-invalid={!!validation} onChange={event => setStart(event.target.value)} /></div>
      <div><label htmlFor="events-end">종료일(KST)</label><input id="events-end" type="date" value={end} max={kstDay()} disabled={busy} required aria-describedby="events-period-help" aria-invalid={!!validation} onChange={event => setEnd(event.target.value)} /></div>
      {tab === 'logs' && <><div><label htmlFor="events-screen">페이지</label><select id="events-screen" value={screen} disabled={busy} onChange={event => setScreen(event.target.value)}><option value="">전체 페이지</option>{summary.data?.screens.map(item => <option key={item.id} value={item.id}>{item.name} · {item.id}</option>)}</select></div>
        <div><label htmlFor="events-type">이벤트 유형</label><select id="events-type" value={type} disabled={busy} onChange={event => setType(event.target.value)}><option value="">전체 유형</option>{Object.entries(types).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></div></>}
      <div className="events-filter-actions"><Button type="submit" disabled={busy}>조회</Button><Button variant="secondary" disabled={busy} onClick={refresh}>새로고침</Button></div>
    </form>
    <p id="events-period-help" className={`admin-muted events-period-help${filtersOpen ? ' is-open' : ''}`}>종료일 포함 최대 31일 · 오늘은 조회 시점까지 고정합니다. {tab !== 'logs' && '페이지·유형 필터는 원시 로그에만 적용하며, 이 탭은 기간 전체 지표입니다.'}</p>
    {validation && <p role="alert" className="admin-error">{validation}</p>}
    <p className="events-window admin-muted">조회 범위 {date(query.window.from)} 이상 / {date(query.window.to)} 미만 KST</p>
    {busy && <p role="status">이벤트 분석을 불러오고 있어요.</p>}
    {error && <div className="admin-error"><p role="alert">이벤트 분석을 불러오지 못했어요.</p><Button variant="secondary" disabled={busy} onClick={refresh}>다시 불러오기</Button></div>}
    {!error && summary.data && <>
      {tab === 'logs' && log.data && <EventRows data={log.data} busy={busy} page={offset => setQuery({ ...query, offset })} />}
      {tab === 'pages' && <PageVisits data={summary.data} />}
      {tab === 'flows' && <Flows data={summary.data} />}
      {tab === 'funnels' && <MissionFunnels data={summary.data} />}
      <p className="admin-muted">페이지 수집 시작 {date(summary.data.collectionStartedAt)} KST · 기존 SYNC 수집 시작 {date(summary.data.syncCollectionStartedAt)} KST</p>
      {summary.data.droppedCompletions > 0 && <p className="admin-note">현재 서버 프로세스에서 완료 로그 {number(summary.data.droppedCompletions)}건을 기록하지 못했어요. 미션 저장 실패를 뜻하지 않으며 이 수치는 서버 재시작 시 초기화됩니다.</p>}
    </>}
    <div className="events-method"><p>화면 이벤트는 최선형 수집으로 누락될 수 있어요. 마지막 관측 단계는 실제 이탈·포기 확정이 아닙니다. 전환율은 기간 내 단계별 관측 인원의 비율이며 동일 회차의 순차 코호트를 뜻하지 않습니다.</p>
      <p>로그인한 일반 참가자만 수집합니다. 인증 전·관리자·개발 미리보기·자동화 브라우저는 제외합니다. 새 페이지 기록은 수집 시작 이후만 조회할 수 있고, 기존 SYNC 질문 기록은 별도 수집 시작일부터 제공됩니다.</p>
      <p>현재 활성 참가자의 모든 회차·문항 버전 관측입니다. 초기화 전 실제 로그는 유지하며, 비활성화·역할 변경에 따라 과거 기간의 표시 인원도 달라질 수 있어요. 추천 노출·실제 클릭·실제 대화나 미션의 인과 효과를 측정하지 않습니다.</p></div>
  </div>;
}
function Empty() { return <p className="admin-empty">조회 기간에 수집된 이벤트가 없어요.</p>; }
function EventRows({ data, busy, page }: { data: EventLog; busy: boolean; page: (offset: number) => void }) {
  return <section className="admin-panel" aria-label="실제 이벤트 로그"><div className="admin-panel-heading"><h2>실제 수집된 이벤트</h2><span className="admin-muted">총 {number(data.total)}건 · 최신순</span></div>
    <p className="admin-muted">시각은 서버 기록 기준입니다. 서버 저장 완료 행에는 실제 완료 시각도 표시합니다. 세션은 인증에 쓸 수 없는 구분값이며, 세션을 알 수 없는 행은 ‘기록 없음’으로 표시합니다.</p>
    {!!data.items.length && <div className="admin-table-wrap"><table className="admin-table events-log-table"><thead><tr><th>서버 기록 시각(KST)</th><th>화면</th><th>이벤트 유형</th><th>미션·단계</th><th>참가자</th><th>세션</th></tr></thead><tbody>{data.items.map(row => <tr key={row.ref}>
      <td data-label="서버 기록 시각(KST)"><time dateTime={row.recordedAt}>{date(row.recordedAt)}</time>{row.source === 'SERVER' && <small>실제 완료 {date(row.eventAt)}</small>}</td>
      <td data-label="화면">{row.screenName}<small className="events-code">{row.screenId}</small></td>
      <td data-label="이벤트 유형">{types[row.eventType as keyof typeof types] ?? row.eventType}<small className="events-code">{row.eventType}</small></td>
      <td data-label="미션·단계">{row.mission ?? missing}<small>{row.step ?? missing} · {row.source === 'SERVER' ? '서버 근거' : row.source === 'SYNC' ? '기존 SYNC' : '화면 관측'}</small></td>
      <td data-label="참가자"><code>{row.participantRef}</code></td><td data-label="세션"><code>{row.sessionRef ?? '기록 없음'}</code></td>
    </tr>)}</tbody></table></div>}
    {!data.items.length && <Empty />}
    <nav className="admin-pager" aria-label="이벤트 로그 페이지"><span>{data.total ? `${number(data.offset + 1)}–${number(data.offset + data.items.length)} / ${number(data.total)}건` : '0건'}</span><div><Button variant="quiet" disabled={busy || data.offset === 0} onClick={() => page(Math.max(0, data.offset - 50))}>이전</Button><Button variant="quiet" disabled={busy || data.nextOffset === null} onClick={() => { if (data.nextOffset !== null) page(data.nextOffset); }}>다음</Button></div></nav>
  </section>;
}
function PageVisits({ data }: { data: EventSummary }) {
  const days = dailyObservations(data.window, data.days);
  return <div className="events-visits"><section className="admin-panel" aria-label="페이지별 방문"><div className="admin-panel-heading"><h2>페이지별 방문</h2><span className="admin-muted">총 {number(data.pages.reduce((sum, row) => sum + row.views, 0))}회</span></div>
    <p className="admin-muted">PAGE_VIEW 관측 페이지만 표시합니다. 재방문자는 같은 페이지를 기간 내 2회 이상 관측한 고유 사용자입니다.</p>
    {!!data.pages.length && <div className="admin-table-wrap"><table className="admin-table events-stats-table"><thead><tr><th>페이지</th><th className="num">총 방문</th><th className="num">고유 방문자</th><th className="num">재방문자</th></tr></thead><tbody>{data.pages.map(row => <tr key={row.screenId}><td data-label="페이지">{row.name}<small className="events-code">{row.screenId}</small></td><td data-label="총 방문" className="num">{number(row.views)}회</td><td data-label="고유 방문자" className="num">{number(row.visitors)}명</td><td data-label="재방문자" className="num">{number(row.repeatVisitors)}명</td></tr>)}</tbody></table></div>}{!data.pages.length && <Empty />}
  </section><section className="admin-panel" aria-label="일자별 방문 추이"><h2>일자별 방문 추이</h2><p className="admin-muted">KST 일자별 실제 PAGE_VIEW 관측입니다. 수집 시작 전 날짜는 {missing}, 수집 이후 관측이 없는 날은 0으로 표시합니다. 페이지별 고유 인원을 합산하지 않습니다.</p><div className="admin-table-wrap"><table className="admin-table events-stats-table"><thead><tr><th>일자(KST)</th><th className="num">방문</th><th className="num">고유 방문자</th></tr></thead><tbody>{days.map(row => { const available = dayCollectionAvailable(row.date, data.collectionStartedAt); return <tr key={row.date}><td data-label="일자(KST)">{row.date}</td><td data-label="방문" className="num">{available ? `${number(row.views)}회` : missing}</td><td data-label="고유 방문자" className="num">{available ? `${number(row.visitors)}명` : missing}</td></tr>; })}</tbody></table></div></section></div>;
}
function Flows({ data }: { data: EventSummary }) {
  const name = (screen: string) => data.screens.find(item => item.id === screen)?.name ?? screen;
  return <section className="admin-panel" aria-label="주요 페이지 이동"><h2>주요 페이지 이동</h2><p className="admin-muted">같은 사용자·세션의 정확한 직전 이벤트가 저장된 이동 상위 20개입니다. 도착 화면의 기록 시각으로 기간을 제한하며, 직전 화면은 기간 밖일 수 있어요. 누락·새 문서·새로고침의 연결을 추정하지 않습니다.</p>
    {!!data.flows.length && <div className="admin-table-wrap"><table className="admin-table events-stats-table"><thead><tr><th>직전 화면</th><th>도착 화면</th><th className="num">이동 횟수</th><th className="num">고유 사용자</th></tr></thead><tbody>{data.flows.map(row => <tr key={`${row.fromScreen}:${row.toScreen}`}><td data-label="직전 화면">{name(row.fromScreen)}<small className="events-code">{row.fromScreen}</small></td><td data-label="도착 화면">{name(row.toScreen)}<small className="events-code">{row.toScreen}</small></td><td data-label="이동 횟수" className="num">{number(row.moves)}회</td><td data-label="고유 사용자" className="num">{number(row.users)}명</td></tr>)}</tbody></table></div>}{!data.flows.length && <Empty />}
  </section>;
}
function MissionFunnels({ data }: { data: EventSummary }) {
  return <>
    {data.funnels.map(funnel => <section key={funnel.mission} className="admin-panel" aria-label={`${funnel.mission} 퍼널`}><div className="admin-panel-heading"><h2>{missions.find(mission => mission.code === funnel.mission)?.name ?? funnel.mission}</h2>{funnel.mission === 'SYNC_ME' && <Link href="/admin/funnel" className="text-link">SYNC 질문별 상세</Link>}</div><div className="admin-table-wrap"><table className="admin-table events-stats-table events-funnel-table"><thead><tr><th>단계</th><th className="num">고유 사용자</th><th className="num">진입 대비 전환율</th><th className="num">직전 단계 대비 전환율</th><th className="num">직전 대비 이탈 추정</th></tr></thead><tbody>{funnel.stages.map(row => <tr key={row.name}><td data-label="단계">{row.name}</td><td data-label="고유 사용자" className="num">{number(row.users)}명</td><td data-label="진입 대비 전환율" className="num">{rate(row.entryConversion)}</td><td data-label="직전 단계 대비 전환율" className="num">{rate(row.previousConversion)}</td><td data-label="직전 대비 이탈 추정" className="num">{rate(row.estimatedDropOff)}</td></tr>)}</tbody></table></div>{funnel.stages.every(row => row.users === 0) && <Empty />}</section>)}
    <p className="admin-note">고유 사용자 수는 단계별 독립 집계입니다. 진입 대비 전환율의 분모는 같은 기간의 해당 미션 페이지 진입 인원이며, 직전 전환율·이탈 추정의 분모는 직전 단계 인원입니다. 분모 0은 —, 관측 누락으로 생긴 100% 초과 전환율·음수 이탈 추정은 그대로 표시합니다.</p>
    <p className="admin-muted">완료는 실제 최초 미션 저장 커밋 이후 기록한 서버 이벤트입니다. 결과 재열람·글 수정·관리자 수동 지급은 새 완료로 세지 않습니다. 연습 TAP은 공식 게임 단계와 분리합니다. 기존 SYNC 질문별 화면은 현재 버전·현재 초기화 회차 기준이므로 이 기간 집계와 분모가 다릅니다.</p>
  </>;
}
