import { Link } from '../../app/navigation';
import { useEffect, useRef, useState } from 'react';
import { Button } from '../../components/ui';
import { Icon } from '../../components/Icon';
import { useParticipation } from '../participation/context';
import { ApiError } from '../auth/api';
import { HeartControl } from './HeartControl';
import { reactionsApi as serverReactionsApi } from './reactions-api';
import { WishComments } from './WishComments';
import type { LikeState } from './reactions-api';
import { createWishFeedback } from './WishToss';
import { WishGallery } from './WishGallery';
import { contentApi as serverContentApi } from './api';
import type { ContentInput, ContentKind, Feed, OwnSlot } from './api';
import { useMissionRuntime } from '../admin/MissionRuntime';
import './cheer-card.css';

const empty: ContentInput = { content: '', templateId: 'PLAIN', style: 'ROSE', anonymous: true, expectedVersion: null };
export function ContentMission({ kind }: { kind: ContentKind }) { const participation = useParticipation(), runtime = useMissionRuntime(); return <ContentMissionView kind={kind} api={runtime?.content ?? serverContentApi} reactions={runtime?.reactions ?? serverReactionsApi} refresh={runtime?.refresh ?? participation.refresh} preview={!!runtime} />; }
export function ContentMissionView({ kind, api: contentApi, reactions: reactionsApi, refresh, preview = false }: { kind: ContentKind; api: typeof serverContentApi; reactions: typeof serverReactionsApi; refresh: () => Promise<void>; preview?: boolean }) {
  const wish = kind === 'wishes'; const participation = { refresh };
  const [slot, setSlot] = useState<OwnSlot | null>(null); const [feed, setFeed] = useState<Feed | null>(null);
  const [input, setInput] = useState<ContentInput>(empty); const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [feedBusy, setFeedBusy] = useState(false);
  const [error, setError] = useState(''); const [feedError, setFeedError] = useState(''); const [uncertain, setUncertain] = useState(false); const [conflict, setConflict] = useState(false);
  const [hearts, setHearts] = useState<Record<number, LikeState>>({}); const [heartLoading, setHeartLoading] = useState(false); const [heartError, setHeartError] = useState('');
  const [wishReceipt, setWishReceipt] = useState<number | null>(null);
  const wishFeedback = useRef(createWishFeedback()); const submitting = useRef(false);
  const feedRequest = useRef(0);
  const active = useRef(true); const textarea = useRef<HTMLTextAreaElement>(null);
  const pending = useRef<{ key: string; input: ContentInput; editing: boolean } | null>(null);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; feedRequest.current++; }; }, [kind]);
  async function loadFeed(cursor?: number) {
    const version = ++feedRequest.current;
    const current = () => active.current && version === feedRequest.current;
    setFeedBusy(true); setFeedError('');
    try { const next = await contentApi.feed(kind, cursor); if (!current()) return false; setFeed(previous => cursor && previous ? { items: [...previous.items, ...next.items.filter(item => !previous.items.some(old => old.id === item.id))], nextCursor: next.nextCursor } : next);
      if (kind === 'cheers' && next.items.length) { setHeartLoading(true); setHeartError(''); try { const states = await reactionsApi.hearts(next.items.map(item => item.id)); if (current()) setHearts(previous => cursor ? { ...previous, ...states } : states); } catch (failure) { if (current()) { if (!cursor) setHearts({}); setHeartError(failure instanceof Error ? failure.message : '하트 상태를 확인하지 못했어요.'); } } }
      return true;
    }
    catch (failure) { if (current()) setFeedError(failure instanceof Error ? failure.message : '공개된 글을 불러오지 못했어요.'); return false; }
    finally { if (current()) { setFeedBusy(false); setHeartLoading(false); } }
  }
  async function load() {
    setLoading(true); setError(''); setWishReceipt(null);
    try { const next = await contentApi.own(kind); if (!active.current) return; setSlot(next); setEditing(!next.id); setInput(empty); pending.current = null; setUncertain(false); setConflict(false); await participation.refresh(); }
    catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '내 글을 확인하지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
    if (active.current) await loadFeed();
  }
  function edit() {
    if (!slot || busy) return; const source = slot.latestRevision;
    setInput(source ? { content: source.content, templateId: source.templateId, style: source.style, anonymous: source.anonymous, expectedVersion: slot.version } : empty);
    setEditing(true); setError(''); setUncertain(false); setConflict(false); pending.current = null;
  }
  async function submit() {
    if (busy || submitting.current || !slot || conflict) return;
    if (!input.content.trim()) { setError(wish ? '분수에 띄울 소원을 적어주세요.' : '동료에게 전할 응원을 적어주세요.'); textarea.current?.focus(); return; }
    if (!pending.current) pending.current = { key: crypto.randomUUID(), editing: !!slot.id, input: { ...input, content: input.content.trim(), expectedVersion: slot.id ? slot.version : null } };
    const attempt = pending.current; submitting.current = true; setBusy(true); setWishReceipt(null); setError('');
    try { const next = await contentApi.submit(kind, attempt.editing, attempt.key, attempt.input); if (active.current) { if (wish) setWishReceipt(wishFeedback.current(next)); setSlot(next); setEditing(false); pending.current = null; setUncertain(false); setInput(empty); await participation.refresh(); await loadFeed(); } }
    catch (failure) { if (active.current) { setError(failure instanceof Error ? failure.message : '제출 응답을 확인하지 못했어요.'); const unknown = !(failure instanceof ApiError) || failure.status >= 500; setUncertain(unknown); setConflict(failure instanceof ApiError && failure.status === 409); if (!unknown) pending.current = null; } }
    finally { submitting.current = false; if (active.current) setBusy(false); }
  }
  if (loading) return <p role="status">내 글과 접수 상태를 확인하고 있어요.</p>;
  const latest = slot?.latestRevision; const locked = busy || uncertain || conflict;
  const feedSection = <section className="content-feed" aria-labelledby="content-feed-heading"><div className="content-section-heading"><h2 id="content-feed-heading">{wish ? '분수에 모인 소원' : '서로에게 건네는 응원'}</h2><Button variant="quiet" disabled={feedBusy} onClick={() => loadFeed()}>목록 새로고침</Button></div>
      {wish && <WishGallery key={wishReceipt ?? 'wishes'} earned={!!slot?.missionCompleted && !preview} items={feed?.items || []} feedReady={feed !== null} receipt={wishReceipt} loadMore={feed?.nextCursor ? () => loadFeed(feed.nextCursor!) : undefined} loadingMore={feedBusy} renderComments={id => <WishComments id={id} api={reactionsApi} />} />}
      {!wish && feed && !feed.items.length && <p className="content-empty">아직 공개된 {wish ? '소원이' : '응원이'} 없어요. 작성한 글이 이곳에 모여요.</p>}
      {!wish && <ul className="content-feed-list">{feed?.items.map(item => <li key={item.id}><article className={`content-card tone-${item.style.toLowerCase()} template-${item.templateId.toLowerCase()}`}><p className="content-body">{item.content}</p><span className="content-byline">{item.anonymous ? '익명' : item.profileId ? <Link href={`/discovery/profiles/${item.profileId}`}>{item.nickname || '동료'}의 프로필 보기</Link> : item.nickname || '동료'}</span><HeartControl id={item.id} initial={hearts[item.id] || null} loading={heartLoading} api={reactionsApi} /></article></li>)}</ul>}
      {heartError && <p role="alert" className="form-notice is-error">{heartError}</p>}
      {feedError && <p role="alert" className="form-notice is-error">{feedError}</p>}{feedBusy && <p role="status">공개된 글을 불러오고 있어요.</p>}
      {!wish && feed?.nextCursor && <Button variant="secondary" disabled={feedBusy} onClick={() => loadFeed(feed.nextCursor!)}>이전 글 더 보기</Button>}
    </section>;
  return <div className={`content-mission ${wish ? 'content-mission--wish' : ''}`}>
    {wish && !slot?.id && feedSection}
    <section className="content-own" aria-labelledby="content-own-heading"><div className="content-section-heading"><h2 id="content-own-heading">{slot?.id ? '내가 남긴 글' : wish ? '분수에 띄울 내 소원' : '동료에게 전할 한마디'}</h2></div>
      {!slot?.id && <p className="result-footnote">익명 또는 닉네임으로 공개할 수 있어요. 한 계정에 한 글을 남길 수 있어요.</p>}
      {slot?.approvedRevision && <div className={`content-card tone-${slot.approvedRevision.style.toLowerCase()} template-${slot.approvedRevision.templateId.toLowerCase()}`}><p className="context-label">공개된 글</p><p className="content-body">{slot.approvedRevision.content}</p><span className="content-byline">{slot.approvedRevision.anonymous ? '익명' : '내 닉네임으로 공개'}</span>{wish && slot.luckScore !== null && <p className="content-luck">나의 운 점수 <strong>{slot.luckScore}</strong></p>}</div>}
      {latest && latest.id !== slot?.approvedRevision?.id && <div className="content-review-state" role="status"><strong>{latest.status === 'REJECTED' ? '수정이 필요해요' : '내가 남긴 글'}</strong><p className="content-body">{latest.content}</p>{latest.status === 'REJECTED' && <p>공개 기준에 맞게 내용을 다듬어 다시 제출해 주세요.</p>}</div>}
      {slot?.visibility === 'HIDDEN' && <p role="status">운영자가 숨긴 글이에요. 글자 기록과 공개 상태는 별도로 관리돼요.</p>}
      {editing && slot && slot.visibility !== 'HIDDEN' && <form onSubmit={event => { event.preventDefault(); void submit(); }} aria-busy={busy}>
        <label htmlFor="content-text">{wish ? '지금, 어떤 소원을 품고 있나요?' : '어떤 응원을 전하고 싶나요?'}</label>
        <textarea id="content-text" ref={textarea} rows={4} maxLength={400} value={input.content} disabled={locked} aria-invalid={error ? true : undefined} aria-describedby="content-help content-count" placeholder={wish ? '함께 이루고 싶은 작은 바람을 적어주세요.' : '오늘도 애쓰는 동료에게 한마디를 남겨주세요.'} onChange={event => { setInput({ ...input, content: event.target.value }); setError(''); }} />
        <div className="wish-input-footer"><p id="content-help">연락처·링크와 타인을 향한 모욕은 적지 말아주세요.</p><span id="content-count">{input.content.length}/400</span></div>
        {!wish && <><fieldset disabled={locked}><legend>카드 형태</legend><label className="content-choice"><input type="radio" name="template" checked={input.templateId === 'PLAIN'} onChange={() => setInput({ ...input, templateId: 'PLAIN' })} />담백한 문장</label><label className="content-choice"><input type="radio" name="template" checked={input.templateId === 'NOTE'} onChange={() => setInput({ ...input, templateId: 'NOTE' })} />작은 편지</label></fieldset><fieldset disabled={locked}><legend>카드 색상</legend><label className="content-choice"><input type="radio" name="style" checked={input.style === 'ROSE'} onChange={() => setInput({ ...input, style: 'ROSE' })} />로즈</label><label className="content-choice"><input type="radio" name="style" checked={input.style === 'INK'} onChange={() => setInput({ ...input, style: 'INK' })} />하늘</label></fieldset><div className={`content-card tone-${input.style.toLowerCase()} template-${input.templateId.toLowerCase()}`} aria-label="응원 카드 미리보기"><p className="context-label">{input.templateId === 'NOTE' ? '동료에게 보내는 작은 편지' : '한 줄의 응원'}</p><p className="content-body">{input.content || '작성한 응원이 여기에 보여요.'}</p><span className="content-byline">{input.anonymous ? '익명' : '내 닉네임으로 공개'}</span></div></>}
        <label className="content-choice"><input type="checkbox" checked={input.anonymous} disabled={locked} onChange={event => setInput({ ...input, anonymous: event.target.checked })} />익명으로 남기기</label><Button type="submit" className="full-width" disabled={busy || conflict}>{busy ? '보내는 중…' : uncertain ? '같은 글 다시 전송하기' : slot.id ? '수정 내용 보내기' : wish ? '동전 던지기' : '응원 보내기'}<Icon name="arrow" size={18} /></Button>
        {uncertain && <p className="result-footnote">응답을 확인하지 못했어요. 같은 요청으로 다시 보내거나 저장된 글을 확인해 주세요.</p>}
        {slot.id && !locked && <Button type="button" variant="quiet" onClick={() => setEditing(false)}>수정 취소하기</Button>}
      </form>}
      {error && <p role="alert" className="form-notice is-error">{error}</p>}
      {!editing && slot?.id && slot.visibility === 'VISIBLE' && <Button variant="secondary" onClick={edit}>같은 글 수정하기</Button>}
      <Button variant="quiet" disabled={busy} onClick={load}>내 글과 접수 상태 새로고침</Button>
    </section>
    {(!wish || !!slot?.id) && feedSection}
  </div>;
}
