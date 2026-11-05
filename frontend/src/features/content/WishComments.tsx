import { Link } from '../../app/navigation';
import { useEffect, useId, useRef, useState } from 'react';
import { Button } from '../../components/ui';
import { ApiError } from '../auth/api';
import { reactionsApi } from './reactions-api';
import type { CommentPage, OwnComment, PublicComment } from './reactions-api';
const statusLabels: Record<OwnComment['status'], string> = { QUEUED: '검수 대기', PROCESSING: '검수 중', APPROVED: '승인됨', REJECTED: '공개되지 않았어요', REVIEW_REQUIRED: '운영자 확인 중', SUPERSEDED: '이전 댓글' };
export function WishComments({ id }: { id: number }) {
  const uid = useId(); const [open, setOpen] = useState(false);
  return <div className="wish-comments"><Button type="button" variant="quiet" aria-expanded={open} aria-controls={uid} onClick={() => setOpen(!open)}>{open ? '댓글 접기' : '댓글 보기'}</Button>{open && <div id={uid}><CommentThread id={id} /></div>}</div>;
}
function CommentThread({ id }: { id: number }) {
  const uid = useId(); const active = useRef(true); const input = useRef<HTMLTextAreaElement>(null);
  const [comments, setComments] = useState<CommentPage<PublicComment> | null>(null); const [own, setOwn] = useState<CommentPage<OwnComment> | null>(null);
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [text, setText] = useState(''); const [reveal, setReveal] = useState(false);
  const [error, setError] = useState(''); const [readError, setReadError] = useState(''); const [uncertain, setUncertain] = useState(false); const [blocked, setBlocked] = useState(false);
  const pending = useRef<{ key: string; text: string; reveal: boolean } | null>(null);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, [id]);
  async function load() {
    setLoading(true); setReadError('');
    try { const nextOwn = await reactionsApi.own(id); if (active.current) { setOwn(nextOwn); const confirmed = pending.current && nextOwn.items.some(item => item.requestKey === pending.current?.key); if (confirmed) { pending.current = null; setText(''); setUncertain(false); } if (!pending.current) { setBlocked(false); setError(''); } } }
    catch (failure) { if (active.current) setReadError(failure instanceof Error ? failure.message : '내 댓글을 확인하지 못했어요.'); }
    try { const next = await reactionsApi.comments(id); if (active.current) setComments(next); }
    catch (failure) { if (active.current) { setComments(null); setReadError(failure instanceof Error ? failure.message : '공개 댓글을 확인하지 못했어요.'); } }
    finally { if (active.current) setLoading(false); }
  }
  async function more(which: 'own' | 'public') {
    const cursor = which === 'own' ? own?.nextCursor : comments?.nextCursor; if (!cursor || loading) return;
    setLoading(true); setReadError('');
    try { if (which === 'own') { const page = await reactionsApi.own(id, cursor); if (active.current) setOwn(previous => ({ items: [...(previous?.items || []), ...page.items], nextCursor: page.nextCursor })); } else { const page = await reactionsApi.comments(id, cursor); if (active.current) setComments(previous => ({ items: [...(previous?.items || []), ...page.items], nextCursor: page.nextCursor })); } }
    catch (failure) { if (active.current) setReadError(failure instanceof Error ? failure.message : '댓글을 더 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  async function submit() {
    if (busy || blocked || !own || !comments) return;
    if (!text.trim()) { setError('동료의 소원에 전할 댓글을 적어주세요.'); input.current?.focus(); return; }
    if (!pending.current) pending.current = { key: crypto.randomUUID(), text: text.trim(), reveal };
    const attempt = pending.current; setBusy(true); setError('');
    try { const saved = await reactionsApi.submit(id, attempt.key, attempt.text, attempt.reveal); if (active.current) { setOwn(previous => ({ items: [saved, ...(previous?.items || []).filter(item => item.id !== saved.id)], nextCursor: previous?.nextCursor || null })); setText(''); pending.current = null; setUncertain(false); } }
    catch (failure) { if (active.current) { setError(failure instanceof Error ? failure.message : '댓글 응답을 확인하지 못했어요.'); const unknown = !(failure instanceof ApiError) || failure.status >= 500; setUncertain(unknown); if (!unknown) { pending.current = null; setBlocked(true); } } }
    finally { if (active.current) setBusy(false); }
  }
  return <section className="comment-thread" aria-label="이 소원의 댓글"><div className="content-section-heading"><h3>소원에 건네는 한마디</h3><Button type="button" variant="quiet" disabled={busy || loading} onClick={load}>댓글 상태 새로고침</Button></div>
    {loading && <p role="status">댓글을 확인하고 있어요.</p>}{readError && <p role="alert" className="form-notice is-error">{readError}</p>}
    {comments && !comments.items.length && <p className="result-footnote">아직 공개된 댓글이 없어요. 승인된 댓글이 여기에 모여요.</p>}
    <ul className="comment-list">{comments?.items.map(item => <li key={item.id}><p className="content-body">{item.content}</p><span className="content-byline">{item.revealProfile ? item.profileId ? <Link href={`/discovery/profiles/${item.profileId}`}>{item.nickname || '동료'}의 프로필 보기</Link> : item.nickname || '동료' : '익명'}</span></li>)}</ul>
    {comments?.nextCursor && <Button type="button" variant="quiet" disabled={loading} onClick={() => more('public')}>이전 공개 댓글 더 보기</Button>}
    {own && <div className="own-comments"><h3>내 댓글 접수 내역</h3>{!own.items.length && <p className="result-footnote">이 소원에 남긴 댓글이 없어요.</p>}<ul className="comment-list">{own.items.map(item => <li key={item.id}><span className="content-status">{statusLabels[item.status]}</span><p className="content-body">{item.content}</p><span className="content-byline">{item.revealProfile ? '닉네임 표시' : '익명'}</span></li>)}</ul>{own.nextCursor && <Button type="button" variant="quiet" disabled={loading} onClick={() => more('own')}>이전 내 댓글 더 보기</Button>}</div>}
    {own && comments && <form className="comment-form" onSubmit={event => { event.preventDefault(); void submit(); }} aria-busy={busy}><label htmlFor={uid}>동료의 소원에 댓글 남기기</label><textarea id={uid} ref={input} rows={3} maxLength={200} value={text} disabled={busy || uncertain || blocked} aria-invalid={error ? true : undefined} aria-describedby={`${uid}-help`} onChange={event => { setText(event.target.value); setError(''); }} /><p id={`${uid}-help`} className="result-footnote">{text.length}/200 · 연락처·링크와 타인에 대한 모욕은 적지 말아주세요.</p><label className="content-choice"><input type="checkbox" checked={reveal} disabled={busy || uncertain || blocked} onChange={event => setReveal(event.target.checked)} />닉네임 표시하기</label><p className="result-footnote">체크하지 않으면 익명으로 표시돼요. 댓글은 검수 후 공개되며 미션 글자는 추가로 지급되지 않아요.</p><Button type="submit" disabled={busy || blocked || loading}>{busy ? '댓글 검수 요청 중…' : uncertain ? '같은 댓글 다시 보내기' : '댓글 검수 요청하기'}</Button></form>}
    {error && <p role="alert" className="form-notice is-error">{error}</p>}
  </section>;
}
