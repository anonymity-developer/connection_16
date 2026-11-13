import { useEffect, useRef, useState } from 'react';
import './tap.css';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { useParticipation } from '../participation/context';
import { resultsApi as serverApi } from './api';
import { tapRankingApi } from './api';
import { TapLeaderboard } from './TapLeaderboard';
import type { TapAttempt, TapPayload, TapState } from './api';
import { cancelTapAttempt } from './cancelRecovery';
import { createTapStartGate, recoverTapStart } from './startRecovery';
import { gameWindow, localPhase } from './timing';
import { useMissionRuntime } from '../admin/MissionRuntime';

interface Run { mode: 'practice' | 'official'; starts: number; ends: number; taps: number[]; attemptId: number | null }
const labels: Record<string, string> = { VALID: '도전을 마쳤어요', INVALID: '기록이 자동 무효 처리됐어요', REVIEW_REQUIRED: '이전 규칙의 기록을 정리하고 있어요', EXPIRED: '제출 시간이 지났어요', INTERRUPTED: '공식 도전이 중단됐어요', CANCELLED: '준비 중 도전을 취소했어요' };
export function TapMission() { const participation = useParticipation(), runtime = useMissionRuntime(); return <TapMissionView api={runtime?.results ?? serverApi} refresh={runtime?.refresh ?? participation.refresh} preview={!!runtime} rankingApi={runtime ? runtime.tapRanking : tapRankingApi} />; }
export function TapMissionView({ api, refresh, preview = false, rankingApi }: { api: typeof serverApi; refresh: () => Promise<void>; preview?: boolean; rankingApi?: typeof tapRankingApi }) {
  const displayLabels = preview ? { ...labels, VALID: '미리보기 결과를 확인했어요', INVALID: '미리보기 무효 기록', REVIEW_REQUIRED: '이전 기록 예시' } : labels;
  const resultsApi = api; const participation = { refresh }; const [state, setState] = useState<TapState | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [running, setRunning] = useState(false); const [tick, setTick] = useState(0); const [count, setCount] = useState(0); const [practiceScore, setPracticeScore] = useState<number | null>(null); const [notice, setNotice] = useState('');
  const run = useRef<Run | null>(null); const pending = useRef<TapPayload | null>(null); const startKey = useRef<string | null>(null); const active = useRef(true); const abort = useRef<() => void>(() => {}); const finishing = useRef(false);
  const startOnce = useRef(createTapStartGate());
  const arena = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!running || !arena.current) return;
    const root = document.documentElement, body = document.body, previousScroll = window.scrollY;
    const previous = { overflow: root.style.overflow, position: body.style.position, top: body.style.top, width: body.style.width };
    arena.current.scrollIntoView({ block: 'start', behavior: 'instant' });
    const gameScroll = window.scrollY;
    // Fixed-body locking also prevents Safari from moving the page during repeated touches.
    root.style.overflow = 'hidden'; body.style.position = 'fixed'; body.style.top = `-${gameScroll}px`; body.style.width = '100%';
    arena.current.querySelector<HTMLButtonElement>('.tap-area')?.focus({ preventScroll: true });
    return () => {
      root.style.overflow = previous.overflow; body.style.position = previous.position; body.style.top = previous.top; body.style.width = previous.width;
      window.scrollTo({ top: previousScroll, behavior: 'instant' });
    };
  }, [running]);
  const phase = run.current ? localPhase(tick, run.current.starts, run.current.ends) : null;
  const remaining = run.current ? Math.max(0, (phase === 'COUNTDOWN' ? run.current.starts : run.current.ends) - tick) / 1000 : 0;
  useEffect(() => {
    active.current = true; void load();
    const hide = () => { if (document.hidden) abort.current(); }; const leave = () => abort.current();
    document.addEventListener('visibilitychange', hide); window.addEventListener('pagehide', leave); window.addEventListener('tagme:navigation', leave);
    return () => { active.current = false; leave(); document.removeEventListener('visibilitychange', hide); window.removeEventListener('pagehide', leave); window.removeEventListener('tagme:navigation', leave); };
  }, []);
  useEffect(() => { if (!running) return; const timer = window.setInterval(() => setTick(performance.now()), 40); return () => clearInterval(timer); }, [running]);
  useEffect(() => {
    if (!running || !run.current || phase !== 'FINISHED' || finishing.current) return;
    const finished = run.current; run.current = null; setRunning(false);
    if (finished.mode === 'practice') { setPracticeScore(finished.taps.length); setNotice('연습을 마쳤어요. 공식 기록과 글자는 바뀌지 않아요.'); return; }
    pending.current = { attemptId: finished.attemptId!, taps: [...finished.taps], interruptions: [] }; void send();
  }, [tick, phase, running]);
  async function load() {
    setLoading(true); setError('');
    try { const next = await resultsApi.tap(); if (active.current) setState(next); }
    catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '공식 도전을 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  async function start() {
    if (busy || running || !state?.officialAvailable) return;
    await startOnce.current(async () => {
    setBusy(true); setError(''); setNotice(''); const before = performance.now();
    try {
      startKey.current ??= crypto.randomUUID();
      const attempt = await resultsApi.start(startKey.current); const received = performance.now();
      if (!active.current) { void resultsApi.cancel(attempt.attemptId).catch(() => {}); return; }
      const current = await recoverTapStart(attempt, resultsApi.tap);
      if (!active.current) return;
      setState(current);
      if (attempt.status === 'CANCELLED') { startKey.current = null; setNotice('취소된 요청을 확인했어요. 현재 공식 도전 상태를 다시 불러왔어요.'); return; }
      if (attempt.status !== 'COUNTDOWN') { setNotice('서버에 이미 시작한 도전이 있어요. 저장된 상태를 확인해 주세요.'); return; }
      const window = gameWindow(attempt.serverNow, attempt.startsAt, attempt.endsAt, received, received - before);
      run.current = { mode: 'official', ...window, taps: [], attemptId: attempt.attemptId }; pending.current = null; finishing.current = false; setCount(0); setTick(received); setRunning(true);
    } catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '도전을 시작하지 못했어요. 같은 요청으로 다시 시도할 수 있어요.'); }
    finally { if (active.current) setBusy(false); }
    });
  }
  function practice() { if (busy || running) return; const now = performance.now(); run.current = { mode: 'practice', starts: now + 5000, ends: now + 15000, taps: [], attemptId: null }; setPracticeScore(null); setCount(0); setTick(now); setRunning(true); setNotice('연습이에요. 공식 기록은 생성되지 않아요.'); }
  function tap() { const game = run.current; if (!game || localPhase(performance.now(), game.starts, game.ends) !== 'PLAYING') return; if (game.taps.length >= 1000) return; game.taps.push(Math.floor(performance.now() - game.starts)); setCount(game.taps.length); }
  async function cancel() {
    const game = run.current; run.current = null; setRunning(false);
    const attemptId = game?.attemptId ?? state?.attempt?.attemptId;
    if (game?.mode === 'practice') { setNotice('연습을 멈췄어요. 공식 도전은 그대로예요.'); return; }
    if (!attemptId || busy) return;
    setBusy(true); setError('');
    try { const current = await cancelTapAttempt(attemptId, resultsApi); if (active.current) { setState(current); if (current.officialAvailable) startKey.current = null; setNotice(current.attempt ? displayLabels[current.attempt.status] ?? '서버의 현재 도전 상태를 확인했어요.' : '취소 후 현재 공식 도전 상태를 확인했어요.'); } }
    catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '중단 상태를 확인하지 못했어요. 공식 기록을 다시 불러와 주세요.'); }
    finally { if (active.current) setBusy(false); }
  }
  abort.current = () => { if (run.current) void cancel(); };
  async function send() {
    if (!pending.current || finishing.current) return;
    finishing.current = true; setBusy(true); setError('');
    try { const next = await resultsApi.finish(pending.current); if (active.current) { setState({ officialAvailable: false, attempt: next }); pending.current = null; await participation.refresh(); } }
    catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '기록을 전송하지 못했어요. 같은 기록으로 다시 시도해 주세요.'); }
    finally { finishing.current = false; if (active.current) setBusy(false); }
  }
  if (loading) return <p role="status">공식 도전 기록을 불러오고 있어요.</p>;
  if (!state) return <div className="tap-panel"><p role="alert">{error}</p><Button variant="secondary" onClick={load}>기록 불러오기 다시 시도</Button></div>;
  const attempt: TapAttempt | null = state?.attempt ?? null;
  const recovered = !running && attempt && ['COUNTDOWN', 'PLAYING', 'AWAITING_SUBMISSION'].includes(attempt.status) && !pending.current;
  const ended = !!attempt && ['VALID', 'INVALID', 'INTERRUPTED', 'EXPIRED', 'REVIEW_REQUIRED'].includes(attempt.status);
  return <section className={`tap-panel${running ? ' is-running' : ''}`} aria-labelledby="tap-heading">
    {running ? <div className={`tap-play${phase === 'PLAYING' ? ' is-playing' : ''}${phase === 'PLAYING' && remaining <= 3 ? ' is-final' : ''}`} ref={arena}>
      <div className="tap-round-heading"><h2 id="tap-heading">{phase === 'COUNTDOWN' ? '준비하세요' : '지금 탭하세요!'}</h2><span className="tap-mode">{run.current?.mode === 'practice' ? '연습' : preview ? '미리보기' : '공식 도전'}</span></div>
      <div className="tap-metrics"><p><span>{phase === 'COUNTDOWN' ? '준비 시간' : '남은 시간'}</span><strong>{remaining.toFixed(1)}<small>초</small></strong></p><p><span>현재 탭</span><strong>{count}<small>회</small></strong></p></div>
      <progress className="tap-time-track" max={phase === 'COUNTDOWN' ? 5 : 10} value={remaining} aria-label={phase === 'COUNTDOWN' ? '준비 시간' : '남은 시간'} />
      <p className="tap-phase-cue" role="status">{phase === 'COUNTDOWN' ? '준비 시간이 끝나면 눌러주세요' : remaining <= 3 ? '마지막까지 빠르게!' : '손끝으로 빠르게 눌러주세요'}</p>
      <button type="button" className="tap-area" data-beat={count % 2} aria-label="탭 영역" aria-disabled={phase !== 'PLAYING'}
        onContextMenu={event => event.preventDefault()}
        onClick={event => { if (event.detail === 0) tap(); }}
        onPointerDown={event => { if (event.button !== 0) return; event.preventDefault(); tap(); }}
        onKeyDown={event => { if ([' ', 'Enter'].includes(event.key)) { event.preventDefault(); if (!event.repeat) tap(); } }}>
        <img src="/images/letters/fast-tap.webp" alt="" draggable={false} />
        <strong>{phase === 'COUNTDOWN' ? Math.max(1, Math.ceil(remaining)) : 'TAP!'}</strong>
        <span>{phase === 'COUNTDOWN' ? '곧 시작해요' : '+1'}</span>
      </button>
      <Button variant="quiet" className="tap-stop" onClick={cancel}>{phase === 'COUNTDOWN' ? '준비 중 취소하기' : '도전 중단하기'}</Button>
    </div> : <>
      <h2 id="tap-heading">{ended ? '나의 기록' : preview ? '10초 미리보기 도전' : '10초의 공식 도전'}</h2>
      {!attempt && <><p className="tap-rules-summary">5초 준비 · 10초 도전 · 공식 1회</p><p className="result-footnote">{preview ? '미리보기 결과는 실제 기록에 반영되지 않아요.' : '준비 중에는 취소할 수 있어요. 시작 후 화면 이탈·중단 시 기회가 사용돼요.'}</p></>}
      <div className="tap-official-state" role="status"><h3>{busy && pending.current ? '공식 기록을 전송하고 있어요' : recovered ? '진행 중인 공식 도전이 있어요' : attempt ? displayLabels[attempt.status] ?? '도전 상태를 확인해 주세요' : '공식 도전을 시작할 수 있어요'}</h3>
        {attempt?.tapCount !== null && attempt?.tapCount !== undefined && <p className="tap-score">{attempt.tapCount}<small>회</small></p>}
        <p>{recovered ? '이전 입력은 복원할 수 없어요. 도전 중단 상태를 확인해 주세요.' : attempt?.status === 'VALID' ? preview ? '미리보기 결과예요. 실제 기록에 반영되지 않아요.' : '공식 기록을 저장했어요.' : attempt?.status === 'INVALID' ? '1ms 미만의 중복 입력 등 입력 규칙에 맞지 않아 무효 처리됐어요. F는 지급되지 않아요.' : attempt?.status === 'REVIEW_REQUIRED' ? '저장된 입력을 현재 규칙으로 확인하고 있어요.' : attempt ? '공식 도전은 다시 시작할 수 없어요.' : '편하게 탭할 수 있도록 휴대폰을 잡아주세요.'}</p>
      </div>
      {state?.officialAvailable && <Button className="full-width" disabled={busy} onClick={start}>{busy ? '시작 확인 중…' : preview ? '미리보기 도전 시작하기' : '공식 도전 시작하기 (1회)'}<Icon name="arrow" size={18} /></Button>}
      {recovered && <Button variant="secondary" disabled={busy} onClick={cancel}>도전 중단 상태 확인하기</Button>}
      {pending.current && <Button disabled={busy} onClick={send}>같은 기록 다시 전송하기</Button>}
      <Button variant="secondary" className="full-width tap-practice" disabled={busy || !!recovered || !!pending.current} onClick={practice}>기록 없이 연습하기</Button>
      {practiceScore !== null && <p className="result-footnote">연습 결과 {practiceScore}회 · 공식 기록에는 반영되지 않아요.</p>}
    </>}
    {!running && notice && <p className="result-footnote" role="status">{notice}</p>}
    {error && <p className="form-notice is-error" role="alert">{error}</p>}
    {!running && attempt?.status === 'REVIEW_REQUIRED' && <Button variant="quiet" disabled={busy} onClick={load}>기록 상태 다시 확인하기</Button>}
    {!running && ended && !pending.current && rankingApi && <TapLeaderboard api={rankingApi} revision={`${attempt?.attemptId ?? 0}:${attempt?.status ?? ''}`} preview={preview} />}
  </section>;
}
