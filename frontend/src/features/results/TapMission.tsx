import { useEffect, useRef, useState } from 'react';
import { Link } from '../../app/navigation';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { useParticipation } from '../participation/context';
import { resultsApi } from './api';
import type { TapAttempt, TapPayload, TapState } from './api';
import { gameWindow, localPhase } from './timing';

interface Run { mode: 'practice' | 'official'; starts: number; ends: number; taps: number[]; attemptId: number | null }
const labels: Record<string, string> = { VALID: '공식 기록을 저장했어요', REVIEW_REQUIRED: '기록을 확인하고 있어요', EXPIRED: '제출 시간이 지났어요', INTERRUPTED: '공식 도전이 중단됐어요', CANCELLED: '준비 중 도전을 취소했어요' };
export function TapMission() {
  const participation = useParticipation(); const [state, setState] = useState<TapState | null>(null); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [running, setRunning] = useState(false); const [tick, setTick] = useState(0); const [count, setCount] = useState(0); const [practiceScore, setPracticeScore] = useState<number | null>(null); const [notice, setNotice] = useState('');
  const run = useRef<Run | null>(null); const pending = useRef<TapPayload | null>(null); const startKey = useRef<string | null>(null); const active = useRef(true); const abort = useRef<() => void>(() => {}); const finishing = useRef(false);
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
    setBusy(true); setError(''); setNotice(''); startKey.current ??= crypto.randomUUID(); const before = performance.now();
    try {
      const attempt = await resultsApi.start(startKey.current); const received = performance.now();
      if (!active.current) { void resultsApi.cancel(attempt.attemptId).catch(() => {}); return; }
      setState({ officialAvailable: false, attempt });
      if (attempt.status !== 'COUNTDOWN') { setNotice('서버에 이미 시작한 도전이 있어요. 저장된 상태를 확인해 주세요.'); return; }
      const window = gameWindow(attempt.serverNow, attempt.startsAt, attempt.endsAt, received, received - before);
      run.current = { mode: 'official', ...window, taps: [], attemptId: attempt.attemptId }; pending.current = null; finishing.current = false; setCount(0); setTick(received); setRunning(true);
    } catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '도전을 시작하지 못했어요. 같은 요청으로 다시 시도할 수 있어요.'); }
    finally { if (active.current) setBusy(false); }
  }
  function practice() { if (busy || running) return; const now = performance.now(); run.current = { mode: 'practice', starts: now + 5000, ends: now + 15000, taps: [], attemptId: null }; setPracticeScore(null); setCount(0); setTick(now); setRunning(true); setNotice('연습이에요. 공식 기록은 생성되지 않아요.'); }
  function tap() { const game = run.current; if (!game || localPhase(performance.now(), game.starts, game.ends) !== 'PLAYING') return; if (game.taps.length >= 1000) return; game.taps.push(Math.floor(performance.now() - game.starts)); setCount(game.taps.length); }
  async function cancel() {
    const game = run.current; run.current = null; setRunning(false);
    const attemptId = game?.attemptId ?? state?.attempt?.attemptId;
    if (game?.mode === 'practice') { setNotice('연습을 멈췄어요. 공식 도전은 그대로예요.'); return; }
    if (!attemptId || busy) return;
    setBusy(true); setError('');
    try { const next = await resultsApi.cancel(attemptId); if (active.current) { setState({ officialAvailable: next.status === 'CANCELLED', attempt: next.status === 'CANCELLED' ? null : next }); if (next.status === 'CANCELLED') startKey.current = null; setNotice(labels[next.status] ?? '도전 상태를 확인했어요.'); } }
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
  const attempt: TapAttempt | null = state?.attempt ?? null;
  const recovered = !running && attempt && ['COUNTDOWN', 'PLAYING', 'AWAITING_SUBMISSION'].includes(attempt.status) && !pending.current;
  return <section className="tap-panel" aria-labelledby="tap-heading"><h2 id="tap-heading">10초의 공식 도전</h2><p className="result-footnote">공식 도전은 1회예요. 5초 준비 중에는 취소할 수 있어요. 시작 후 화면 이탈이나 중단은 기회를 사용한 것으로 기록돼요.</p>
    {running ? <div className="tap-play"><p className="tap-mode">{run.current?.mode === 'practice' ? '연습 · 공식 기록 없음' : '공식 도전'}</p><div className="tap-metrics"><p><span>{phase === 'COUNTDOWN' ? '준비 시간' : '남은 시간'}</span><strong>{remaining.toFixed(1)}<small>초</small></strong></p><p><span>현재 탭</span><strong>{count}<small>회</small></strong></p></div><button type="button" className="tap-area" aria-label="탭 영역" onPointerDown={event => { event.preventDefault(); tap(); }} onKeyDown={event => { if ([' ', 'Enter'].includes(event.key)) { event.preventDefault(); if (!event.repeat) tap(); } }}>{phase === 'COUNTDOWN' ? '곧 시작해요' : '탭!'}<span>{phase === 'COUNTDOWN' ? '준비 시간이 끝나면 눌러주세요' : '손끝으로 빠르게 눌러주세요'}</span></button><Button variant="quiet" onClick={cancel}>{phase === 'COUNTDOWN' ? '준비 중 취소하기' : '도전 중단하기'}</Button></div>
      : <><div className="tap-official-state" role="status"><h3>{busy && pending.current ? '공식 기록을 전송하고 있어요' : recovered ? '진행 중인 공식 도전이 있어요' : attempt ? labels[attempt.status] ?? '도전 상태를 확인해 주세요' : '공식 도전을 시작할 수 있어요'}</h3>{attempt?.tapCount !== null && attempt?.tapCount !== undefined && <p className="tap-score">{attempt.tapCount}<small>회</small></p>}<p>{recovered ? '화면을 새로 열어 이전 입력을 복원할 수 없어요. 중단 상태를 확인해 주세요.' : attempt?.status === 'VALID' ? '서버가 이벤트를 확인한 잠정 기록이에요. 글자는 지급됐고 수상 순위는 별도 검토 후 확정돼요.' : attempt?.status === 'REVIEW_REQUIRED' ? '비정상 간격이 포함돼 검토가 필요해요. 아직 글자는 지급되지 않았어요.' : attempt ? '공식 도전은 새로 시작되지 않아요. 필요한 경우 운영자에게 문의해 주세요.' : '준비 시간을 포함해 안정적으로 참여할 수 있을 때 시작해 주세요.'}</p></div>
      {state?.officialAvailable && <Button className="full-width" disabled={busy} onClick={start}>{busy ? '시작 확인 중…' : '공식 도전 시작하기 (1회)'}<Icon name="arrow" size={18} /></Button>}
      {recovered && <Button variant="secondary" disabled={busy} onClick={cancel}>도전 중단 상태 확인하기</Button>}
      {pending.current && <Button disabled={busy} onClick={send}>같은 기록 다시 전송하기</Button>}
      <Button variant="secondary" className="full-width tap-practice" disabled={busy || !!recovered || !!pending.current} onClick={practice}>기록 없이 연습하기</Button>{practiceScore !== null && <p className="result-footnote">연습 결과 {practiceScore}회 · 공식 기록에는 반영되지 않아요.</p>}
    </>}
    {notice && <p className="result-footnote" role="status">{notice}</p>}{error && <p className="form-notice is-error" role="alert">{error}</p>}{!running && <Button variant="quiet" disabled={busy} onClick={load}>서버 기록 다시 확인하기</Button>}<Link className="text-link" href="/">수집판으로 돌아가기<Icon name="arrow" size={16} /></Link>
  </section>;
}
