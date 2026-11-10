import { useEffect, useRef, useState } from 'react';
import { Link } from '../../app/navigation';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { LetterSticker } from '../../components/LetterSticker';
import { useParticipation } from '../participation/context';
import { ApiError } from '../auth/api';
import { resultsApi as serverApi } from './api';
import type { SyncQuestions, SyncResult } from './api';
import { useMissionRuntime } from '../admin/MissionRuntime';
import './sync-result.css';
export function SyncMission() { const participation = useParticipation(), runtime = useMissionRuntime(); return <SyncMissionView api={runtime?.results ?? serverApi} refresh={runtime?.refresh ?? participation.refresh} />; }
export function SyncMissionView({ api, refresh, stepwise = false }: { api: typeof serverApi; refresh: () => Promise<void>; stepwise?: boolean }) {
  const resultsApi = api; const participation = { refresh }; const [questionIndex, setQuestionIndex] = useState(0); const [questions, setQuestions] = useState<SyncQuestions | null>(null); const [result, setResult] = useState<SyncResult | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({}); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [changed, setChanged] = useState(false);
  const active = useRef(true); const form = useRef<HTMLFormElement>(null);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, []);
  async function load() {
    setLoading(true); setError(''); setChanged(false);
    try { const saved = await resultsApi.sync(); if (!active.current) return; setResult(saved.result); if (!saved.result) { const next = await resultsApi.questions(); if (active.current) { setQuestions(next); setAnswers({}); } } }
    catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '질문을 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  async function submit() {
    if (busy || !questions?.available || !questions.version) return;
    const missing = questions.questions.find(q => !answers[q.id]);
    if (missing) { setError('아직 답하지 않은 질문이 있어요.'); form.current?.querySelector<HTMLInputElement>(`input[name="${missing.id}"]`)?.focus(); return; }
    setBusy(true); setError('');
    try { const saved = await resultsApi.submitSync(questions.version, questions.questions.map(q => ({ questionId: q.id, optionId: answers[q.id] }))); if (active.current) { setResult(saved); await participation.refresh(); } }
    catch (failure) { if (active.current) { setError(failure instanceof Error ? failure.message : '결과를 저장하지 못했어요.'); setChanged(failure instanceof ApiError && failure.status === 409); } }
    finally { if (active.current) setBusy(false); }
  }
  if (loading) return <p role="status">성향 기록을 불러오고 있어요.</p>;
  if (result) return <SyncResultCard result={result} />;
  return <section className="sync-panel" aria-labelledby="sync-heading"><h2 id="sync-heading">나의 취향을 골라요</h2><p className="result-footnote">마음에 가까운 답을 골라주세요. 제출 전까지 바꿀 수 있어요.</p>
    {questions?.available ? <form ref={form} onSubmit={event => { event.preventDefault(); void submit(); }} aria-busy={busy}><div className="sync-question-list">{questions.questions.map((q, index) => <fieldset hidden={stepwise && index !== questionIndex} key={q.id} disabled={busy || changed}><legend>{q.text}</legend>{q.options.map(option => <label className="sync-option" key={option.id}><input type="radio" name={q.id} value={option.id} checked={answers[q.id] === option.id} onChange={() => { setAnswers(previous => ({ ...previous, [q.id]: option.id })); setError(''); }} /><span>{option.label}</span></label>)}</fieldset>)}</div><p className="result-footnote">{Object.keys(answers).length}/{questions.questions.length}개 응답</p><div className="sync-progress" role="progressbar" aria-label="설문 진행률" aria-valuenow={Object.keys(answers).length} aria-valuemin={0} aria-valuemax={questions.questions.length}><span style={{width:`${Object.keys(answers).length / questions.questions.length * 100}%`}} /></div>{stepwise && <p className="sync-step">질문 {questionIndex+1} / {questions.questions.length}</p>}{stepwise && <div className="demo-actions"><Button type="button" variant="quiet" disabled={questionIndex === 0} onClick={() => setQuestionIndex(questionIndex - 1)}>이전 질문</Button><Button type="button" variant="secondary" disabled={!answers[questions.questions[questionIndex].id] || questionIndex >= questions.questions.length - 1} onClick={() => setQuestionIndex(questionIndex + 1)}>다음 질문</Button></div>}{(!stepwise || questionIndex === questions.questions.length - 1) && <Button type="submit" className="full-width" disabled={busy || changed}>{busy ? '결과 확인 중…' : '나의 성향 확인하기'}<Icon name="arrow" size={18} /></Button>}</form> : <p className="result-footnote">성향 질문을 준비하고 있어요. 운영자가 문항과 유형을 확정한 뒤 참여할 수 있어요.</p>}
    {error && <p className="form-notice is-error" role="alert">{error}</p>}{(!questions || changed) && <Button variant="secondary" onClick={load}>질문과 저장된 결과 다시 불러오기</Button>}
  </section>;
}

export function SyncResultCard({ result }: { result: SyncResult }) {
  const preview = !!useMissionRuntime();
  return <section className="sync-result sync-result-ticket" aria-labelledby="sync-result-heading">
    <header className="sync-ticket-header"><div><p className="context-label">SYNC ME · 나의 최초 결과</p><h2 id="sync-result-heading">나의 성향 결과</h2></div><LetterSticker code="SYNC_ME" /></header>
    {!preview && <p className="content-status" role="status">두 번째 S를 획득했어요!</p>}
    <div className="sync-ticket-type"><span className="sync-ticket-code" aria-label={`성향 코드 ${result.type.code}`}>{result.type.code}</span><p className="sync-type-title">{result.type.title}</p></div>
    <div className="sync-ticket-description"><p className="context-label">이런 대화를 나눠보세요</p><p>{result.type.description}</p></div>
    <footer className="sync-ticket-footer"><p className="result-footnote">동료와 대화를 시작하기 위한 재미용 성향이에요. 전문적인 성격 진단이 아니며 최초 결과는 다시 바뀌지 않아요.</p>{preview ? <p className="result-footnote">관리자 미리보기 결과예요. 실제 동료 조회나 방문 기록은 만들지 않아요.</p> : <Link className="button button--primary" href="/discovery">나와 통하는 동료 만나기<Icon name="arrow" size={18} /></Link>}</footer>
  </section>;
}
