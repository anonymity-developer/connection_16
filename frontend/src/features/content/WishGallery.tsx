import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { CSSProperties, ReactNode, Ref } from 'react';
import { Button, Dialog } from '../../components/ui';
import { Link } from '../../app/navigation';
import type { PublicContent } from './api';
import { WishComments } from './WishComments';
import { WishLakeArt } from './WishLake';
import { WishToss } from './WishToss';
import './wish-lake.css';

const boatPositions = [
  [18, 22, -7], [50, 18, 4], [82, 23, 8], [28, 62, 5],
  [70, 63, -5], [18, 94, 8], [83, 92, -8],
] as const;
const boatColours = ['coral', 'mint', 'sun', 'sky', 'rose', 'cream', 'leaf'] as const;

export function lakePage(items: PublicContent[], page: number, size = 7) {
  const unique = [...new Map(items.map(item => [item.id, item])).values()];
  const pages = Math.max(1, Math.ceil(unique.length / size));
  const index = Math.max(0, Math.min(Number.isFinite(page) ? Math.floor(page) : 0, pages - 1));
  return { items: unique.slice(index * size, (index + 1) * size), index, pages, total: unique.length };
}

function useBoatCount() {
  const [count, setCount] = useState(6);
  useEffect(() => {
    const update = () => setCount(window.innerWidth <= 360 ? 5 : window.innerWidth < 768 ? 6 : 7);
    update(); window.addEventListener('resize', update); return () => window.removeEventListener('resize', update);
  }, []);
  return count;
}

function PaperBoat({ colour }: { colour: typeof boatColours[number] }) {
  return <svg className={`paper-boat paper-boat--${colour}`} viewBox="0 0 92 64" aria-hidden="true" focusable="false">
    <path className="paper-boat-sail" d="M45 5 15 39h30Z" /><path className="paper-boat-sail paper-boat-sail--right" d="m48 9 28 30H48Z" />
    <path className="paper-boat-hull" d="M5 39h82L70 59H23Z" /><path className="paper-boat-fold" d="m23 59 24-20 23 20" />
  </svg>;
}

const comments = (id: number) => <WishComments id={id} embedded />;
type WishView = 'lake' | 'list';

export function WishCard({ item, children }: { item: PublicContent; children?: ReactNode }) {
  return <article className="wish-detail-card"><p className="content-byline">{item.anonymous ? '익명의 소원' : item.profileId ? <Link href={`/discovery/profiles/${item.profileId}`}>{item.nickname || '동료'}의 소원</Link> : `${item.nickname || '동료'}의 소원`}</p><p className="content-body">{item.content}</p>{children}</article>;
}

export function WishGallery({ items, receipt = null, earned = false, feedReady = true, initialView = 'lake', sceneOverlay, sceneRef, renderComments = comments, loadMore, loadingMore = false, ownItem = null, openOwnRequest = 0 }: {
  sceneOverlay?: ReactNode; sceneRef?: Ref<HTMLDivElement>; items: PublicContent[]; receipt?: number | null; earned?: boolean; feedReady?: boolean; initialView?: WishView; renderComments?: (id: number) => ReactNode; loadMore?: () => Promise<boolean>; loadingMore?: boolean; ownItem?: PublicContent | null; openOwnRequest?: number;
}) {
  const [view, setView] = useState<WishView>(initialView);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [batchIds, setBatchIds] = useState<number[]>(() => [...new Map(items.map(item => [item.id, item])).values()].slice(0, 6).map(item => item.id));
  const [waitingForMore, setWaitingForMore] = useState(false);
  const seen = useRef(new Set<number>());
  const handledOwnRequest = useRef(0);
  const advanceAfterLoad = useRef(false);
  const boatCount = useBoatCount();
  const uid = useId();
  const unique = useMemo(() => [...new Map(items.map(item => [item.id, item])).values()], [items]);
  const detailItems = useMemo(() => ownItem && !unique.some(item => item.id === ownItem.id) ? [ownItem, ...unique] : unique, [ownItem, unique]);
  const selectedIndex = detailItems.findIndex(item => item.id === selectedId);
  const selected = selectedIndex < 0 ? null : detailItems[selectedIndex];

  useEffect(() => { if (openOwnRequest && openOwnRequest !== handledOwnRequest.current && ownItem) { handledOwnRequest.current = openOwnRequest; setSelectedId(ownItem.id); } }, [openOwnRequest, ownItem]);

  useEffect(() => {
    const target = Math.min(boatCount, unique.length);
    const valid = batchIds.length === target && batchIds.every(id => unique.some(item => item.id === id));
    if (valid && !advanceAfterLoad.current) { batchIds.forEach(id => seen.current.add(id)); return; }
    const fresh = unique.filter(item => !seen.current.has(item.id));
    const previous = unique.filter(item => seen.current.has(item.id) && !batchIds.includes(item.id));
    const current = unique.filter(item => batchIds.includes(item.id));
    const candidates = [...fresh, ...previous, ...current];
    const next = candidates.slice(0, boatCount).map(item => item.id);
    next.forEach(id => seen.current.add(id));
    advanceAfterLoad.current = false;
    setBatchIds(next); setWaitingForMore(false);
  }, [unique, boatCount, waitingForMore, batchIds]);

  const batch = batchIds.map(id => unique.find(item => item.id === id)).filter((item): item is PublicContent => !!item);
  async function meetNewWishes() {
    setSelectedId(null);
    batchIds.forEach(id => seen.current.add(id));
    const unseen = unique.filter(item => !seen.current.has(item.id));
    if (unseen.length >= boatCount) {
      const next = unseen.slice(0, boatCount).map(item => item.id);
      next.forEach(id => seen.current.add(id)); setBatchIds(next); return;
    }
    if (loadMore) {
      setWaitingForMore(true);
      const loaded = await loadMore();
      if (loaded) { advanceAfterLoad.current = true; setWaitingForMore(false); return; }
      setWaitingForMore(false);
    }
    if (!unseen.length) seen.current.clear();
    const fresh = unique.filter(item => !seen.current.has(item.id) && !batchIds.includes(item.id));
    const previous = unique.filter(item => !batchIds.includes(item.id) && !fresh.some(candidate => candidate.id === item.id));
    const current = unique.filter(item => batchIds.includes(item.id));
    const next = [...fresh, ...previous, ...current].slice(0, boatCount).map(item => item.id);
    next.forEach(id => seen.current.add(id)); setBatchIds(next);
  }

  return <div className="wish-gallery">
    <div className="wish-view-switch" role="group" aria-label="소원 보기 방식">
      <Button type="button" variant="quiet" aria-pressed={view === 'lake'} onClick={() => setView('lake')}>소원 호수</Button>
      <Button type="button" variant="quiet" aria-pressed={view === 'list'} onClick={() => setView('list')}>소원 목록</Button>
    </div>

    {view === 'lake' && <section className="wish-lake-view" aria-labelledby={`${uid}-lake-title`}>
      <div className="wish-lake-copy"><h3 id={`${uid}-lake-title`}>종이배에 담긴 소원</h3><p>종이배를 눌러 동료의 소원과 댓글을 만나보세요.</p></div>
      <div ref={sceneRef} className={`wish-lake-scene ${receipt !== null ? 'has-receipt' : ''}`}>
        <WishLakeArt />
        <ul className="wish-boats" aria-label="호수에 떠 있는 소원">
          {batch.map((item, index) => {
            const [x, y, angle] = boatPositions[index];
            const style = { '--boat-x': `${x}%`, '--boat-y': `${y}%`, '--boat-angle': `${angle}deg`, '--boat-scale': `${0.82 + (index % 3) * 0.11}`, '--float-delay': `${-index * 0.7}s` } as CSSProperties;
            return <li key={item.id} style={style}><button type="button" aria-label={`${item.anonymous ? '익명' : item.nickname || '동료'}의 소원 ${index + 1} 열기`} onClick={() => setSelectedId(item.id)}><PaperBoat colour={boatColours[index]} /><span>{item.content}</span></button></li>;
          })}
        </ul>
        {!batch.length && <p className="wish-lake-invitation">{feedReady ? <>아직 호수에 종이배가 없어요.<br />첫 소원을 기다리고 있어요.</> : '소원 호수를 불러오고 있어요.'}</p>}
        {receipt !== null && <WishToss key={receipt} earned={earned} />}{sceneOverlay}
      </div>
      {!!batch.length && <Button className="wish-refresh" type="button" variant="secondary" disabled={loadingMore || waitingForMore} onClick={() => void meetNewWishes()}>{loadingMore || waitingForMore ? '다른 소원을 찾는 중…' : '새로운 소원 만나기'}</Button>}
    </section>}

    {view === 'list' && <section className="wish-list-view" aria-labelledby={`${uid}-list-title`}>
      <div className="wish-list-heading"><div><h3 id={`${uid}-list-title`}>소원 목록</h3><p>호수에 공개된 소원을 천천히 둘러보세요.</p></div><span>{unique.length}개 불러옴</span></div>
      {!unique.length && feedReady && <p className="content-empty">아직 공개된 소원이 없어요.</p>}
      <ul className="wish-card-list" aria-label="소원 목록">{unique.map(item => <li key={item.id}><button type="button" onClick={() => setSelectedId(item.id)}><span className="wish-list-boat"><PaperBoat colour={boatColours[item.id % boatColours.length]} /></span><span><b>{item.anonymous ? '익명의 소원' : `${item.nickname || '동료'}의 소원`}</b><span>{item.content}</span><small>댓글 {item.commentCount ?? 0}개</small></span></button></li>)}</ul>
      {loadMore && <Button type="button" variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? '불러오는 중…' : '소원 더 보기'}</Button>}
    </section>}

    <Dialog open={!!selected} title="소원과 댓글" className="wish-detail-sheet" onClose={() => setSelectedId(null)}>
      {selected && <><nav className="wish-detail-nav" aria-label="다른 소원 보기"><Button type="button" variant="quiet" aria-label="이전 소원" disabled={selectedIndex <= 0} onClick={() => setSelectedId(detailItems[selectedIndex - 1].id)}>←</Button><span>{selectedIndex + 1} / {detailItems.length}</span><Button type="button" variant="quiet" aria-label="다음 소원" disabled={selectedIndex >= detailItems.length - 1} onClick={() => setSelectedId(detailItems[selectedIndex + 1].id)}>→</Button></nav><WishCard key={selected.id} item={selected}>{renderComments(selected.id)}</WishCard></>}
    </Dialog>
  </div>;
}
