import { useEffect, useId, useRef, useState } from 'react';
import type { CSSProperties, ReactNode, Ref } from 'react';
import { Button } from '../../components/ui';
import type { PublicContent } from './api';
import { WishComments } from './WishComments';
import { WishLakeArt } from './WishLake';
import { WishToss } from './WishToss';
import './wish-lake.css';

const STACKED_COIN_COUNT = 3;
// Resting points leave room to drift inside the basin and keep each tap area fixed.
const underwaterPositions = [
  [27, 67], [59, 60.5], [75, 69.2], [47, 74.5],
  [69, 81], [25, 81.8], [47, 89],
] as const;
export const LAKE_PAGE_SIZE = STACKED_COIN_COUNT + underwaterPositions.length;
/** Only receives the server's public feed, never own/pending revisions. */
export function lakePage(items: PublicContent[], page: number) {
  const unique = [...new Map(items.map(item => [item.id, item])).values()];
  const pages = Math.max(1, Math.ceil(unique.length / LAKE_PAGE_SIZE));
  const index = Math.max(0, Math.min(Number.isFinite(page) ? Math.floor(page) : 0, pages - 1));
  return { items: unique.slice(index * LAKE_PAGE_SIZE, (index + 1) * LAKE_PAGE_SIZE), index, pages, total: unique.length };
}
const comments = (id: number) => <WishComments id={id} />;
type WishView = 'lake' | 'cloud';
export function WishCard({ item, children }: { item: PublicContent; children?: ReactNode }) {
  // Wishes remain anonymous even if an unexpected display name is present in a response.
  return <article className="content-card wish-discovery-card"><p className="content-byline">익명의 소원</p><p className="content-body">{item.content}</p>{children}</article>;
}
export function WishGallery({ items, receipt = null, earned = false, feedReady = true, initialView = 'lake', sceneOverlay, sceneRef, renderComments = comments, loadMore, loadingMore = false }: {
  sceneOverlay?: ReactNode; sceneRef?: Ref<HTMLDivElement>; items: PublicContent[]; receipt?: number | null; earned?: boolean; feedReady?: boolean; initialView?: WishView; renderComments?: (id: number) => ReactNode; loadMore?: () => Promise<boolean>; loadingMore?: boolean;
}) {
  const [view, setView] = useState<WishView>(initialView); const [page, setPage] = useState(0); const [selectedId, setSelectedId] = useState<number | null>(null);
  const uid = useId(); const detail = useRef<HTMLHeadingElement>(null); const trigger = useRef<HTMLButtonElement | null>(null);
  const current = lakePage(items, page); const selected = items.find(item => item.id === selectedId); const selectedExists = !!selected;
  useEffect(() => { if (selectedExists) detail.current?.focus(); }, [selectedId, selectedExists]);
  const switchView = (next: WishView) => { setView(next); setSelectedId(null); };
  const move = (next: number) => { setPage(next); setSelectedId(null); };
  const moreInPond = current.index + 1 < current.pages;
  async function nextPond() {
    if (moreInPond || await loadMore?.()) move(current.index + 1);
  }
  return <div className="wish-gallery">
    <div className="wish-view-switch" role="group" aria-label="소원 보기 방식">
      <Button type="button" variant="quiet" aria-pressed={view === 'lake'} onClick={() => switchView('lake')}>분수로 보기</Button>
      <Button type="button" variant="quiet" aria-pressed={view === 'cloud'} onClick={() => switchView('cloud')}>구름으로 보기</Button>
    </div>
    <div hidden={view !== 'lake'}>
      <p className="wish-lake-help" id={`${uid}-help`}>A 앞의 동전이나 물속 그림자를 눌러 소원을 만나보세요.</p>
      <div ref={sceneRef} className={`wish-lake-scene fountain-scene ${receipt !== null ? 'has-receipt' : ''}`} aria-describedby={`${uid}-help`}>
        <WishLakeArt />
        <ul className="wish-lake-coins" aria-label="분수에 공개된 소원">
          {current.items.map((item, index) => {
            const number = current.index * LAKE_PAGE_SIZE + index + 1;
            const position = underwaterPositions[index - STACKED_COIN_COUNT];
            const [x, y] = position ?? [50, 40.5];
            const style = { '--coin-x': `${x}%`, '--coin-y': `${y}%`, '--coin-size': `${17 + index % 4 * 2}px`, '--drift-delay': `${-index * 3.1}s`, '--drift-duration': `${15 + index % 4 * 3}s` } as CSSProperties;
            return <li key={item.id} className={position ? undefined : 'wish-coin-stacked'} data-tip={x <= 32 ? 'start' : x >= 68 ? 'end' : undefined} style={style}>
              <button type="button" className="wish-lake-coin" aria-label={`소원 ${number} 열기`} aria-describedby={`${uid}-tip-${item.id}`} aria-expanded={item.id === selectedId} aria-controls={`${uid}-detail`} onClick={event => { trigger.current = event.currentTarget; setSelectedId(item.id); }}>
                {position ? <span className="wish-coin-shadow" aria-hidden="true" /> : <svg className="wish-coin-gold" viewBox="0 0 34 22" aria-hidden="true">
                  <path d="M2 10v4c0 4 7 7 15 7s15-3 15-7v-4Z" fill="#b58443" />
                  <ellipse cx="17" cy="10" rx="15" ry="7.5" fill="#ffe29a" stroke="#b58443" />
                  <ellipse cx="17" cy="10" rx="11" ry="4.7" fill="none" stroke="#c7984f" />
                  <path d="M6 8c2-2 6-3 10-3" fill="none" stroke="#fff7d6" strokeLinecap="round" />
                </svg>}
                <span className="wish-coin-tooltip" id={`${uid}-tip-${item.id}`} role="tooltip">{item.content}</span>
              </button>
            </li>;
          })}
        </ul>
        {!current.total && <p className="wish-lake-invitation">{feedReady ? <>아직 공개된 소원이 없어요.<br />첫 소원을 기다리고 있어요.</> : '공개 소원을 불러오면 동전으로 만나요.'}</p>}
        {receipt !== null && view === 'lake' && <WishToss key={receipt} earned={earned} />}{sceneOverlay}
      </div>
      {(current.pages > 1 || loadMore) && <div className="wish-lake-paging" aria-label="분수의 다른 소원 보기">
        <Button type="button" variant="quiet" disabled={current.index === 0 || loadingMore} onClick={() => move(current.index - 1)}>이전 동전</Button>
        <span role="status">{current.index * LAKE_PAGE_SIZE + 1}–{current.index * LAKE_PAGE_SIZE + current.items.length}번 소원</span>
        <Button type="button" variant="quiet" disabled={(!moreInPond && !loadMore) || loadingMore} onClick={() => void nextPond()}>{loadingMore ? '불러오는 중…' : moreInPond || !loadMore ? '다음 동전' : '소원 더 불러오기'}</Button>
      </div>}
    </div>
    {view === 'cloud' && <div className="wish-sky">
      <div className="wish-sky-heading">
        <p>구름에 담긴 소원</p>
        <svg className="wish-sky-flight" viewBox="0 0 230 84" aria-hidden="true" focusable="false">
          <path d="M2 71c46 6 99-1 143-29" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeDasharray="4 7" />
          <g transform="translate(142 34) rotate(-19)" stroke="#317daa" strokeWidth="2.3" strokeLinejoin="round">
            <path d="M1 7-4-9l12 1 11 9h15L23-18l8-2L52 0h19c10 0 12 11 0 15H52L34 34l-10-1 10-18H11Z" fill="white" />
            <path d="M64 6h8M52 6h3M41 6h3" strokeLinecap="round" />
          </g>
        </svg>
      </div>
      {!current.total && feedReady && <p className="content-empty">아직 공개된 소원이 없어요. 동료의 소원이 이곳에 모여요.</p>}
      <ul className="wish-clouds" aria-label="구름 소원 목록">{items.map((item, index) => <li key={item.id}><button type="button" aria-expanded={item.id === selectedId} aria-controls={`${uid}-detail`} onClick={event => { trigger.current = event.currentTarget; setSelectedId(item.id); }}>
        <svg className="wish-cloud-shape" viewBox="0 0 320 154" preserveAspectRatio="none" aria-hidden="true" focusable="false"><path vectorEffect="non-scaling-stroke" d="M48 142C22 142 7 127 7 107c0-18 10-31 27-37-1-23 18-39 42-37 12-24 51-29 74-9 17-22 55-23 73 1 24-8 52 7 55 30 22 4 35 22 35 41 0 27-19 43-44 43-13 11-36 14-54 6-19 9-43 9-64 0-20 8-42 7-58 0-16 7-32 6-45-3Z" /></svg>
        <span className="content-byline">익명의 소원 {index + 1}</span><span className="wish-cloud-text">{item.content}</span>
      </button></li>)}</ul>
      {loadMore && <Button type="button" variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? '불러오는 중…' : '소원 더 불러오기'}</Button>}
    </div>}
    <div id={`${uid}-detail`} className="wish-lake-detail">
      {selected ? <><div className="wish-detail-heading"><h3 ref={detail} tabIndex={-1}>발견한 소원</h3><Button type="button" variant="quiet" onClick={() => { setSelectedId(null); trigger.current?.focus(); }}>소원 닫기</Button></div><WishCard key={selected.id} item={selected}>{renderComments(selected.id)}</WishCard></> : current.total > 0 && <p className="result-footnote">동전이나 구름을 선택해 소원과 댓글을 열어보세요.</p>}
    </div>
  </div>;
}
