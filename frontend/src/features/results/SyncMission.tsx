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
import { discoveryApi } from '../discovery/api';
import type { Person } from '../discovery/api';
import './sync-result.css';
export function SyncMission() { const participation = useParticipation(), runtime = useMissionRuntime(); return <SyncMissionView api={runtime?.results ?? serverApi} refresh={runtime?.refresh ?? participation.refresh} />; }
export function SyncMissionView({ api, refresh, stepwise = true, sameTypePeople, onPeerSelect }: { api: typeof serverApi; refresh: () => Promise<void>; stepwise?: boolean; sameTypePeople?: Pick<Person, 'id' | 'nickname'>[]; onPeerSelect?: (id: number) => void }) {
  const resultsApi = api; const participation = { refresh }; const [questionIndex, setQuestionIndex] = useState(0); const [questions, setQuestions] = useState<SyncQuestions | null>(null); const [result, setResult] = useState<SyncResult | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({}); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [changed, setChanged] = useState(false);
  const active = useRef(true); const form = useRef<HTMLFormElement>(null);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, []);
  async function load() {
    setLoading(true); setError(''); setChanged(false);
    try { const saved = await resultsApi.sync(); if (!active.current) return; setResult(saved.result); if (!saved.result) { const next = await resultsApi.questions(); if (active.current) { setQuestions(next); setAnswers({}); setQuestionIndex(0); } } }
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
  if (result) return <SyncResultCard result={result} sameTypePeople={sameTypePeople} onPeerSelect={onPeerSelect} />;
  return <section className="sync-panel" aria-labelledby="sync-heading"><h2 id="sync-heading">{questions?.title || '나의 취향을 골라요'}</h2><p className="result-footnote">마음에 가까운 답을 골라주세요. 제출 전까지 바꿀 수 있어요.</p>
    {questions?.available ? <form ref={form} onSubmit={event => { event.preventDefault(); void submit(); }} aria-busy={busy}>{stepwise && <><div className="sync-question-status"><p className="sync-step">질문 {questionIndex + 1} / {questions.questions.length}</p><p className="result-footnote">{Object.keys(answers).length}개 응답 완료</p></div><div className="sync-progress" role="progressbar" aria-label="설문 진행률" aria-valuenow={questionIndex + 1} aria-valuemin={1} aria-valuemax={questions.questions.length}><span style={{width:`${(questionIndex + 1) / questions.questions.length * 100}%`}} /></div></>}<div className="sync-question-list">{questions.questions.map((q, index) => <fieldset hidden={stepwise && index !== questionIndex} key={q.id} disabled={busy || changed}><legend>{q.text}</legend>{q.options.map(option => <label className="sync-option" key={option.id}><input type="radio" name={q.id} value={option.id} checked={answers[q.id] === option.id} onChange={() => { setAnswers(previous => ({ ...previous, [q.id]: option.id })); setError(''); }} /><span>{option.label}</span></label>)}</fieldset>)}</div>{stepwise ? <div className="sync-question-navigation"><Button type="button" variant="quiet" disabled={questionIndex === 0 || busy || changed} onClick={() => setQuestionIndex(index => index - 1)}>이전</Button>{questionIndex === questions.questions.length - 1 ? <Button type="submit" disabled={busy || changed || !answers[questions.questions[questionIndex].id]}>{busy ? '결과 확인 중…' : '결과 확인하기'}<Icon name="arrow" size={18} /></Button> : <Button type="button" variant="secondary" disabled={busy || changed || !answers[questions.questions[questionIndex].id]} onClick={() => setQuestionIndex(index => index + 1)}>다음</Button>}</div> : <><p className="result-footnote">{Object.keys(answers).length}/{questions.questions.length}개 응답</p><div className="sync-progress" role="progressbar" aria-label="설문 진행률" aria-valuenow={Object.keys(answers).length} aria-valuemin={0} aria-valuemax={questions.questions.length}><span style={{width:`${Object.keys(answers).length / questions.questions.length * 100}%`}} /></div><Button type="submit" className="full-width" disabled={busy || changed}>{busy ? '결과 확인 중…' : '결과 확인하기'}<Icon name="arrow" size={18} /></Button></>}</form> : <p className="result-footnote">성향 질문을 준비하고 있어요. 운영자가 문항과 유형을 확정한 뒤 참여할 수 있어요.</p>}
    {error && <p className="form-notice is-error" role="alert">{error}</p>}{(!questions || changed) && <Button variant="secondary" onClick={load}>질문과 저장된 결과 다시 불러오기</Button>}
  </section>;
}

export function SyncResultCard({ result, sameTypePeople, onPeerSelect }: { result: SyncResult; sameTypePeople?: Pick<Person, 'id' | 'nickname'>[]; onPeerSelect?: (id: number) => void }) {
  const preview = !!useMissionRuntime();
  return <section className="sync-result sync-result-ticket" aria-labelledby="sync-result-heading">
    <header className="sync-ticket-header"><div><p className="context-label">SYNC ME · 나의 최초 결과</p><h2 id="sync-result-heading">나의 성향 결과</h2></div><LetterSticker code="SYNC_ME" /></header>

    <div className="sync-ticket-type"><span className="sync-ticket-code" aria-label={`성향 코드 ${result.type.code}`}>{result.type.code}</span><p className="sync-type-title">{result.type.title}</p></div>
    <div className="sync-ticket-description"><p className="context-label">나의 성향 한 줄</p><p>{result.type.description}</p></div>
    {!preview && <SameTypePeers result={result} staticPeople={sameTypePeople} onPeerSelect={onPeerSelect} />}
    <footer className="sync-ticket-footer"><p className="result-footnote">동료와 대화를 시작하기 위한 재미용 성향이에요. 전문적인 성격 진단이 아니며 최초 결과는 다시 바뀌지 않아요.</p>{preview ? <p className="result-footnote">관리자 미리보기 결과예요. 실제 동료 조회나 방문 기록은 만들지 않아요.</p> : <Link className="text-link" href="/discovery">전체 동료 둘러보기<Icon name="arrow" size={16} /></Link>}</footer>
  </section>;
}

function SameTypePeers({ result, staticPeople, onPeerSelect }: { result: SyncResult; staticPeople?: Pick<Person, 'id' | 'nickname'>[]; onPeerSelect?: (id: number) => void }) {
  const [people, setPeople] = useState<Pick<Person, 'id' | 'nickname'>[]>(staticPeople ?? []);
  const [cursor, setCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(!staticPeople);
  const [error, setError] = useState('');
  const active = useRef(true);
  useEffect(() => { active.current = true; if (staticPeople) { setPeople(staticPeople); setCursor(null); setLoading(false); setError(''); } else void load(); return () => { active.current = false; }; }, [result.questionnaireVersion, result.type.code, staticPeople]);
  async function load(next?: number) {
    setLoading(true); setError('');
    const filters = new URLSearchParams({ typeCode: result.type.code, questionnaireVersion: result.questionnaireVersion });
    try {
      const page = await discoveryApi.people(filters, next);
      if (!active.current) return;
      setPeople(current => next ? [...current, ...page.items] : page.items);
      setCursor(page.nextCursor);
    } catch (failure) {
      if (active.current) setError(failure instanceof Error ? failure.message : '같은 성향의 동료를 불러오지 못했어요.');
    } finally { if (active.current) setLoading(false); }
  }
  return <section className="sync-same-type" aria-labelledby="sync-same-type-heading">
    <div><h3 id="sync-same-type-heading">나와 같은 성향의 동료</h3><p className="result-footnote">닉네임을 누르면 동료의 프로필을 볼 수 있어요.</p></div>
    {people.length > 0 && <ul className="sync-peer-list">{people.map(person => <li key={person.id}>{onPeerSelect ? <button type="button" onClick={() => onPeerSelect(person.id)}>{person.nickname}<Icon name="arrow" size={16} /></button> : <Link href={`/discovery/profiles/${person.id}`}>{person.nickname}<Icon name="arrow" size={16} /></Link>}</li>)}</ul>}
    {!loading && !error && people.length === 0 && <p className="sync-peer-empty">아직 같은 유형으로 공개된 동료가 없어요.</p>}
    {loading && <p className="sync-peer-status" role="status">같은 성향의 동료를 불러오고 있어요.</p>}
    {error && <div className="sync-peer-retry"><p role="alert">{error}</p><Button variant="secondary" onClick={() => void load(cursor ?? undefined)}>다시 불러오기</Button></div>}
    {!loading && !error && cursor && <Button variant="secondary" onClick={() => void load(cursor)}>동료 더 보기</Button>}
  </section>;
}
