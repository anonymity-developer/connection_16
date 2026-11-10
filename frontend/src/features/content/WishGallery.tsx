import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode, Ref } from 'react';
import { Button } from '../../components/ui';
import type { PublicContent } from './api';
import { WishComments } from './WishComments';
import { WishLakeArt } from './WishLake';
import { WishToss } from './WishToss';
import './wish-lake.css';

export const LAKE_PAGE_SIZE = 6;
/** Only receives the server's public feed, never own/pending revisions. No random positions. */
export function lakePage(items: PublicContent[], page: number) {
  const unique = [...new Map(items.map(item => [item.id, item])).values()];
  const pages = Math.max(1, Math.ceil(unique.length / LAKE_PAGE_SIZE));
  const index = Math.max(0, Math.min(Number.isFinite(page) ? Math.floor(page) : 0, pages - 1));
  return { items: unique.slice(index * LAKE_PAGE_SIZE, (index + 1) * LAKE_PAGE_SIZE), index, pages, total: unique.length };
}
const comments = (id: number) => <WishComments id={id} />;
export function WishCard({ item, children }: { item: PublicContent; children?: ReactNode }) {
  // Wishes remain anonymous even if an unexpected display name is present in a response.
  return <article className="content-card wish-discovery-card"><p className="content-byline">익명의 소원</p><p className="content-body">{item.content}</p>{children}</article>;
}
export function WishGallery({ items, receipt = null, feedReady = true, initialView = 'lake', statueIds = [], sceneOverlay, sceneRef, renderComments = comments }: {
  sceneOverlay?: ReactNode; sceneRef?: Ref<HTMLDivElement>; items: PublicContent[]; receipt?: number | null; feedReady?: boolean; initialView?: 'lake' | 'list'; statueIds?: number[]; renderComments?: (id: number) => ReactNode;
}) {
  const [view, setView] = useState<'lake' | 'cloud' | 'list'>(initialView); const [page, setPage] = useState(0); const [selectedId, setSelectedId] = useState<number | null>(null);
  const uid = useId(); const detail = useRef<HTMLHeadingElement>(null); const trigger = useRef<HTMLButtonElement | null>(null);
  const current = lakePage(items, page); const selected = items.find(item => item.id === selectedId); const selectedExists = !!selected;
  useEffect(() => { if (selectedExists && view !== 'list') detail.current?.focus(); }, [selectedId, selectedExists, view]);
  const move = (next: number) => { setPage(next); setSelectedId(null); };
  return <div className="wish-gallery">
    <div className="wish-view-switch" role="group" aria-label="소원 보기 방식">
      <Button type="button" variant="quiet" aria-pressed={view === 'lake'} onClick={() => setView('lake')}>분수로 보기</Button>
      <Button type="button" variant="quiet" aria-pressed={view === 'cloud'} onClick={() => setView('cloud')}>구름으로 보기</Button>
      <Button type="button" variant="quiet" aria-pressed={view === 'list'} onClick={() => setView('list')}>목록으로 보기</Button>
    </div>
    <div hidden={view !== 'lake'}>
      <p className="wish-lake-help" id={`${uid}-help`}>분수 속 동전을 눌러 동료의 소원을 만나보세요.</p>
      <div ref={sceneRef} className={`wish-lake-scene fountain-scene ${receipt !== null ? 'has-receipt' : ''}`} aria-describedby={`${uid}-help`}>
        <WishLakeArt />
        <ul className="wish-lake-coins" aria-label="분수에 공개된 소원">
          {current.items.map((item, index) => <li key={item.id} className={item.id === statueIds.find(id => current.items.some(row => row.id === id)) ? 'is-on-statue' : undefined}><button type="button" className="wish-lake-coin" aria-describedby={`${uid}-tip-${item.id}`} aria-label={`소원 ${current.index * LAKE_PAGE_SIZE + index + 1} 열기`} aria-expanded={item.id === selectedId} aria-controls={`${uid}-detail`} onClick={event => { trigger.current = event.currentTarget; setSelectedId(item.id); }}><svg viewBox="0 0 44 44" aria-hidden="true"><circle cx="22" cy="22" r="17" /><circle cx="22" cy="22" r="11" fill="none" /></svg><span id={`${uid}-tip-${item.id}`} className="wish-coin-tooltip" role="tooltip">{item.content}</span><span aria-hidden="true">{current.index * LAKE_PAGE_SIZE + index + 1}</span></button></li>)}
        </ul>
        {!current.total && <p className="wish-lake-invitation">{feedReady ? <>아직 공개된 소원이 없어요.<br />승인된 소원을 기다리고 있어요.</> : '공개 소원을 불러오면 동전으로 만나요.'}</p>}
        {receipt !== null && <WishToss key={receipt} inLake />}{sceneOverlay}
      </div>
      {current.pages > 1 && <div className="wish-lake-paging" aria-label="불러온 소원 넘기기"><Button type="button" variant="quiet" disabled={current.index === 0} onClick={() => move(current.index - 1)}>이전 소원</Button><span role="status">{current.index + 1} / {current.pages}</span><Button type="button" variant="quiet" disabled={current.index + 1 >= current.pages} onClick={() => move(current.index + 1)}>다음 소원</Button></div>}
    </div>
    {view === 'cloud' && !current.total && feedReady && <p className="content-empty">아직 공개된 소원이 없어요. 승인된 글이 이곳에 모여요.</p>}
    {view === 'cloud' && <ul className="wish-clouds" aria-label="구름 소원 목록">{items.map((item,index) => <li key={item.id}><button type="button" aria-expanded={item.id === selectedId} aria-controls={`${uid}-detail`} onClick={event => { trigger.current = event.currentTarget; setSelectedId(item.id); }}><span className="content-byline">익명의 소원 {index+1}</span><span>{item.content}</span></button></li>)}</ul>}
      <div id={`${uid}-detail`} className="wish-lake-detail">
        {selected && view !== 'list' ? <><div className="wish-detail-heading"><h3 ref={detail} tabIndex={-1}>발견한 소원</h3><Button type="button" variant="quiet" onClick={() => { setSelectedId(null); trigger.current?.focus(); }}>소원 닫기</Button></div><WishCard key={selected.id} item={selected}>{renderComments(selected.id)}</WishCard></> : view !== 'list' && current.total > 0 && <p className="result-footnote">소원 내용과 댓글은 동전을 선택하면 열려요. 목록으로도 읽을 수 있어요.</p>}
      </div>
    {view === 'list' && <>
      {receipt !== null && <p role="status" className="result-footnote">퐁당! 소원이 접수됐어요. A는 첫 검수 승인 후 받아요.</p>}
      {!current.total && feedReady && <p className="content-empty">아직 공개된 소원이 없어요. 승인된 글이 이곳에 모여요.</p>}
      <ul className="content-feed-list wish-list-view" aria-label="공개 소원 목록">{lakePage(items, 0).total > 0 && [...new Map(items.map(item => [item.id, item])).values()].map(item => <li key={item.id}><WishCard item={item}>{renderComments(item.id)}</WishCard></li>)}</ul>
    </>}
  </div>;
}
