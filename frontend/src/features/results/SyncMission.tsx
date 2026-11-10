import { useEffect, useRef, useState } from 'react';
import { Link } from '../../app/navigation';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { useParticipation } from '../participation/context';
import { ApiError } from '../auth/api';
import { resultsApi } from './api';
import type { SyncQuestions, SyncResult } from './api';
import { useMissionRuntime } from '../admin/MissionRuntime';
export function SyncMission() {
  const runtime = useMissionRuntime(); const api = runtime?.results ?? resultsApi;
  const participation = useParticipation(); const [questions, setQuestions] = useState<SyncQuestions | null>(null); const [result, setResult] = useState<SyncResult | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({}); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [changed, setChanged] = useState(false);
  const active = useRef(true); const form = useRef<HTMLFormElement>(null);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, []);
  async function load() {
    setLoading(true); setError(''); setChanged(false);
    try { const saved = await api.sync(); if (!active.current) return; setResult(saved.result); if (!saved.result) { const next = await api.questions(); if (active.current) { setQuestions(next); setAnswers({}); } } }
    catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '질문을 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  async function submit() {
    if (busy || !questions?.available || !questions.version) return;
    const missing = questions.questions.find(q => !answers[q.id]);
    if (missing) { setError('아직 답하지 않은 질문이 있어요.'); form.current?.querySelector<HTMLInputElement>(`input[name="${missing.id}"]`)?.focus(); return; }
    setBusy(true); setError('');
    try { const saved = await api.submitSync(questions.version, questions.questions.map(q => ({ questionId: q.id, optionId: answers[q.id] }))); if (active.current) { setResult(saved); await (runtime?.refresh ?? participation.refresh)(); } }
    catch (failure) { if (active.current) { setError(failure instanceof Error ? failure.message : '결과를 저장하지 못했어요.'); setChanged(failure instanceof ApiError && failure.status === 409); } }
    finally { if (active.current) setBusy(false); }
  }
  if (loading) return <p role="status">성향 기록을 불러오고 있어요.</p>;
  if (result) return <section className="sync-result" aria-labelledby="sync-result-heading"><p className="context-label">나의 최초 결과 · {result.type.code}</p><h2 id="sync-result-heading">나의 성향 결과</h2><p className="sync-type-title">{result.type.title}</p><p>{result.type.description}</p><p className="result-footnote">동료와 대화를 시작하기 위한 재미용 성향이에요. 전문적인 성격 진단이 아니며 최초 결과는 다시 바뀌지 않아요.</p><Link className="button button--primary" href="/">수집판으로 돌아가기<Icon name="arrow" size={18} /></Link></section>;
  return <section className="sync-panel" aria-labelledby="sync-heading"><h2 id="sync-heading">나의 취향을 골라요</h2><p className="result-footnote">답변은 제출 전까지 바꿀 수 있어요. 제출하면 서버가 계산한 최초 결과 1회만 저장돼요.</p>
    {questions?.available ? <form ref={form} onSubmit={event => { event.preventDefault(); void submit(); }} aria-busy={busy}><div className="sync-question-list">{questions.questions.map(q => <fieldset key={q.id} disabled={busy || changed}><legend>{q.text}</legend>{q.options.map(option => <label className="sync-option" key={option.id}><input type="radio" name={q.id} value={option.id} checked={answers[q.id] === option.id} onChange={() => { setAnswers(previous => ({ ...previous, [q.id]: option.id })); setError(''); }} /><span>{option.label}</span></label>)}</fieldset>)}</div><p className="result-footnote">{Object.keys(answers).length}/{questions.questions.length}개 응답</p><Button type="submit" className="full-width" disabled={busy || changed}>{busy ? '결과 저장 중…' : '최초 결과 확정하기'}<Icon name="arrow" size={18} /></Button></form> : <p className="result-footnote">성향 질문을 준비하고 있어요. 운영자가 문항과 유형을 확정한 뒤 참여할 수 있어요.</p>}
    {error && <p className="form-notice is-error" role="alert">{error}</p>}{(!questions || changed) && <Button variant="secondary" onClick={load}>질문과 저장된 결과 다시 불러오기</Button>}
  </section>;
}
