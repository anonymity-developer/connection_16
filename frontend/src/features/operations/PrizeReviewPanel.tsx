import { useEffect, useState } from 'react';
import { Link } from '../../app/navigation';
import { Button } from '../../components/ui';
import { ApiError, request } from '../auth/api';
import { operationsApi } from './api';
import type { Content, Page } from './api';

const labels: Record<string, string> = {
  CHICKEN_1: '전체 글자 수집 완료 1위',
  CHICKEN_7: '전체 글자 수집 완료 7위',
  CHICKEN_77: '전체 글자 수집 완료 77위',
  DISCOVERY: 'SYNC ME',
  COMMENTS: 'A WISH COME TRUE',
  FAST_TAP: 'FAST TAP',
  HEARTS: 'YOU GOT THIS',
};
const criteria: Record<string, string> = {
  DISCOVERY: '가장 많은 동료의 프로필을 탐색한 교육생',
  COMMENTS: '다른 교육생의 소원에 가장 많은 응원 댓글을 작성한 교육생 · 같은 소원당 1건',
  FAST_TAP: '가장 높은 탭 기록을 달성한 교육생',
  HEARTS: '가장 많은 하트를 받은 응원 메시지를 작성한 교육생',
};
const kinds: Record<string, string> = { WISH: '소원', CHEER: '응원', COMMENT: '소원 댓글' };
const units: Record<string, string> = { DISCOVERY: '명 방문', COMMENTS: '개 소원', FAST_TAP: '탭', HEARTS: '하트' };
const time = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'short', timeStyle: 'medium', timeZone: 'Asia/Seoul',
});
type Person = { userId: number; name: string; classNo: number };
type Candidate = { rank: number; person: Person; score: number | null; completedAt: string | null };
type Group = { code: string; targetRank: number; candidates: Candidate[] };
type Review = { observedAt: string; groups: Group[]; drawEntries: { person: Person; tickets: number }[] };
type OnDenied = (message: string) => void;
const name = (person: Person) => `${person.name.replace(/\s+/g, ' ').trim()}(${person.classNo}반)`;

function message(failure: unknown, onDenied: OnDenied) {
  const text = failure instanceof Error ? failure.message : '정보를 불러오지 못했어요.';
  if (failure instanceof ApiError && (failure.status === 401 || failure.status === 403)) onDenied(text);
  return text;
}

export function PrizeReviewPanel({ onDenied }: { onDenied: OnDenied }) {
  const [review, setReview] = useState<Review | null>(null);
  const [selection, setSelection] = useState<Record<string, number>>({});
  const [author, setAuthor] = useState<Person | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState('');
  const [copyStatus, setCopyStatus] = useState('');

  useEffect(() => {
    let current = true;
    request<Review>('/admin/prize-review').then(result => {
      if (current) setReview(result);
    }).catch(failure => {
      if (current) setError(message(failure, onDenied));
    });
    return () => { current = false; };
  }, [refresh]); // The administrator gate callback can change during session polling.

  const excluded = new Set(Object.values(selection).filter(Boolean));
  const entries = review?.drawEntries.filter(entry => !excluded.has(entry.person.userId)) ?? [];
  const tickets = entries.flatMap(entry => Array<string>(entry.tickets).fill(name(entry.person)));
  const drawingText = tickets.join('\n');
  const ambiguousNames = new Set(entries.map(entry => name(entry.person))).size !== entries.length;
  const targetGroups = new Map<number, string[]>();
  for (const group of review?.groups ?? []) {
    for (const candidate of group.candidates.filter(row => row.rank === group.targetRank)) {
      const id = candidate.person.userId;
      targetGroups.set(id, [...(targetGroups.get(id) ?? []), group.code]);
    }
  }

  return <section className="ops-awards" aria-labelledby="prize-heading">
    <div className="ops-toolbar prize-toolbar">
      <div><h2 id="prize-heading">TAG ME 이벤트 수상 기준</h2>
        <p className="ops-meta">{review ? `조회 ${time.format(new Date(review.observedAt))} KST` : '운영 미션 기록 기준'}</p>
        <Link className="text-link" href="/admin/moderation?tab=awards&view=legacy">기존 시상 근거 조회</Link>
      </div>
      <Button variant="secondary" disabled={!review && !error} onClick={() => {
        setReview(null); setSelection({}); setAuthor(null); setError(''); setCopyStatus(''); setRefresh(refresh + 1);
      }}>다시 조회</Button>
    </div>
    {error && <p role="alert" className="form-notice is-error">{error}</p>}
    {!review && !error && <p role="status">시상 후보를 조회하고 있어요.</p>}
    {review && <>
      <a className="text-link" href="#prize-draw-heading">추첨 명단으로 이동</a>
      <h3>공통 선정 기준</h3>
      <ul className="ops-meta">
        <li>중복 당첨 제외 · 1인 1개 상품 지급</li>
        <li>동점자 발생 시 · 프로필 등록 시점이 빠른 교육생 우선</li>
      </ul>
      <p className="ops-meta">부문별 수상자를 선택하면 추첨 명단에서 제외돼요. 선택은 이 화면에서만 유지돼요.</p>
      <ul className="award-candidates">{review.groups.map(group => {
        const targets = group.candidates.filter(row => row.rank === group.targetRank);
        const selected = group.candidates.find(row => row.person.userId === selection[group.code]);
        const shown = selected && !targets.includes(selected) ? [...targets, selected] : targets;
        return <li key={group.code}>
          {group.code === 'CHICKEN_1' && <h3>행운의 완주상 | BHC 뿌링클 + 콜라 (3명)</h3>}
          {group.code === 'DISCOVERY' && <h3>미션별 우수상 | 스타벅스 1만원권 (4명)</h3>}
          <h4>{labels[group.code]}</h4>
          {criteria[group.code] && <p className="ops-meta">{criteria[group.code]}</p>}
          {!targets.length && <p className="ops-meta">해당 순위 후보가 없어요.</p>}
          {shown.map(({ person, rank, score, completedAt }) => {
            const overlaps = targetGroups.get(person.userId)?.filter(code => code !== group.code) ?? [];
            const otherSelections = Object.keys(selection).filter(code => code !== group.code && selection[code] === person.userId);
            return <div key={person.userId}>
              <div className="award-row">
                <strong>{rank}위 · {name(person)} <small>#{person.userId}</small></strong>
                <span>{completedAt ? time.format(new Date(completedAt)) : score !== null && `${score.toLocaleString('ko-KR')} ${units[group.code] ?? ''}`}</span>
                <Button variant="secondary" onClick={() => setAuthor(person)}>작성 내용 보기</Button>
              </div>
              {overlaps.length > 0 && <p className="award-warning">중복 후보 · {overlaps.map(code => labels[code]).join(' · ')}</p>}
              {otherSelections.length > 0 && <p className="award-warning">다른 부문 수상자로 선택됨 · {otherSelections.map(code => labels[code]).join(' · ')}</p>}
            </div>;
          })}
          {group.candidates.length > 0 && <div className="award-category">
            <label htmlFor={`prize-${group.code}`}>추첨 제외 수상자</label>
            <select id={`prize-${group.code}`} value={selection[group.code] || ''} onChange={event => {
              setSelection({ ...selection, [group.code]: Number(event.target.value) });
              setCopyStatus('');
            }}>
              <option value="">선택 안 함</option>
              {group.candidates.map(row => <option key={row.person.userId} value={row.person.userId}
                disabled={row.person.userId !== selection[group.code] && excluded.has(row.person.userId)}>
                {row.rank}위 · {name(row.person)} (#{row.person.userId})
              </option>)}
            </select>
          </div>}
        </li>;
      })}</ul>
      {author && <Writing key={author.userId} person={author} onClose={() => setAuthor(null)} onDenied={onDenied} />}
      <section className="award-review" aria-labelledby="prize-draw-heading">
        <h3 id="prize-draw-heading">참여의 기쁨상 | 스타벅스 아이스 아메리카노 (20명)</h3>
        <p>대상 {entries.length}명 · 추첨권 {tickets.length}장 · 수상자 선택 {excluded.size}명</p>
        <p className="ops-meta">미션 참여량에 따라 당첨 확률에 가중치를 부여하여 추첨해요. 참여도가 높을수록 당첨 확률이 높아져요.</p>
        <p className="ops-meta">유효 획득 글자당 한 줄이에요. 외부에서 한 명을 뽑을 때마다 그 사람의 줄을 모두 제거하세요.</p>
        {entries.length < 20 && <p className="award-warning">현재 추첨 대상이 20명보다 적어요.</p>}
        {ambiguousNames && <p className="award-warning">이름과 반이 같은 참가자가 있어요. 외부 추첨 전에 동명이인을 구분해 주세요.</p>}
        <textarea className="prize-drawing-text" aria-label="가중치 추첨 명단" value={drawingText} readOnly rows={8} />
        <Button variant="secondary" disabled={!drawingText} onClick={async () => {
          try {
            await navigator.clipboard.writeText(drawingText);
            setCopyStatus('추첨 명단을 복사했어요.');
          } catch {
            setCopyStatus('명단을 선택해서 직접 복사해 주세요.');
          }
        }}>명단 복사</Button>
        <p role="status">{copyStatus}</p>
      </section>
    </>}
  </section>;
}

function Writing({ person, onClose, onDenied }: { person: Person; onClose: () => void; onDenied: OnDenied }) {
  const [page, setPage] = useState<Page<Content> | null>(null);
  const [items, setItems] = useState<Content[]>([]);
  const [parents, setParents] = useState<Record<number, Content>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState<HTMLDialogElement | null>(null);
  useEffect(() => { if (dialog && !dialog.open) dialog.showModal(); }, [dialog]);
  useEffect(() => {
    let current = true;
    void load(null, () => current);
    return () => { current = false; };
  }, []);

  async function load(before: number | null, current = () => true) {
    setLoading(true);
    setError('');
    try {
      const result = await request<Page<Content>>(`/admin/prize-review/writing/${person.userId}${before ? `?before=${before}` : ''}`);
      if (current()) { setPage(result); setItems(previous => before ? [...previous, ...result.items] : result.items); }
    } catch (failure) {
      if (current()) setError(message(failure, onDenied));
    } finally {
      if (current()) setLoading(false);
    }
  }

  return <dialog ref={setDialog} className="prize-writing" aria-labelledby="prize-writing-heading" onCancel={onClose}>
    <div className="ops-toolbar"><h3 id="prize-writing-heading">{name(person)} · 작성 내용</h3>
      <Button variant="secondary" onClick={onClose}>닫기</Button></div>
    {loading && <p role="status">작성 내용을 불러오고 있어요.</p>}
    {error && <p role="alert">{error}</p>}
    {page && !items.length && <p>현재 작성 기록이 없어요.</p>}
    {items.map(item => {
      const parentId = item.parentWishId;
      const parent = parentId ? parents[parentId] : null;
      const revisions = [
        { label: '승인된 원문', revision: item.approvedRevision },
        { label: '최근 제출 원문', revision: item.latestRevision?.id === item.approvedRevision?.id ? null : item.latestRevision },
      ];
      return <article key={item.id} className="ops-revision">
        <h4>{kinds[item.kind]} #{item.id} · {item.visibility === 'HIDDEN' ? '운영자 숨김' : item.approvedRevision ? '공개 허용' : '공개본 없음'}</h4>
        {revisions.map(({ label, revision }) => revision && <section key={revision.id}>
          <p className="ops-meta">{label} · {time.format(new Date(revision.submittedAt))}</p>
          <p className="ops-body">{revision.content}</p>
        </section>)}
        {parentId && (parent
          ? <p className="ops-body">대상 소원 #{parentId} · {(parent.approvedRevision ?? parent.latestRevision)?.content ?? '원문 없음'}</p>
          : <Button variant="secondary" onClick={async () => {
            try {
              const result = await operationsApi.content(parentId);
              setParents(previous => ({ ...previous, [parentId]: result }));
            } catch (failure) {
              setError(message(failure, onDenied));
            }
          }}>대상 소원 보기</Button>)}
      </article>;
    })}
    {(page?.nextCursor || (!page && error)) && <Button variant="secondary" disabled={loading} onClick={() => void load(page?.nextCursor ?? null)}>
      {page ? '더 보기' : '다시 시도'}
    </Button>}
  </dialog>;
}
