import { useEffect, useId, useRef, useState } from 'react';
import { Link } from '../../app/navigation';
import { ProfileAvatar } from '../../components/ProfileAvatar';
import { Dialog } from '../../components/ui';
import type { Graph, Person } from './api';
import { networkLayout } from './layout';
import './network.css';

function Selection({ person }: { person: Person }) {
  return <div className="network-person"><ProfileAvatar name={person.nickname} /><h3>{person.nickname}</h3>
    {person.bio && <p className="person-bio">{person.bio}</p>}
    <p className="person-interests">{person.interests.map(interest => interest.name).join(' · ')}</p>
    <p className="person-relation">{person.mutual ? '서로 봄' : person.visited ? '내가 봄' : '나를 봄'}</p>
    <Link className="button button--primary" href={`/discovery/profiles/${person.id}`}>프로필 보기</Link></div>;
}

export function NetworkFigure({ graph }: { graph: Graph }) {
  const marker = `visit-arrow-${useId().replaceAll(':', '')}`;
  const canvas = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(280), [selectedId, setSelectedId] = useState<number | null>(null);
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 640px)').matches);
  useEffect(() => {
    const element = canvas.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => { if (entry.contentRect.width > 0) setWidth(entry.contentRect.width); });
    observer.observe(element);
    const media = window.matchMedia('(max-width: 640px)');
    const changed = () => setMobile(media.matches);
    media.addEventListener('change', changed);
    return () => { observer.disconnect(); media.removeEventListener('change', changed); };
  }, []);
  const points = networkLayout(graph, width);
  const selected = graph.nodes.find(person => person.id === selectedId);
  const desktopSelection = selected || points[0]?.person;
  return <div className="network-explorer"><figure className="discovery-graph"><div ref={canvas} className="network-canvas"><svg viewBox="0 0 320 320" aria-hidden="true">
    <defs><marker id={marker} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="4" markerHeight="4" orient="auto-start-reverse"><path d="M0 0L10 5L0 10Z" /></marker></defs>
    {points.map(({ person, x, y, incoming, outgoing }) => {
      const distance = Math.hypot(x - 160, y - 160);
      const inset = Math.min(.45, 32 * 320 / (width * distance));
      return <g key={person.id} className={selectedId === null || person.id === selectedId ? 'network-active' : 'network-muted'}><line style={{ strokeWidth: incoming && outgoing ? 3 : 1.5 }} x1={160 + (x - 160) * .24} y1={160 + (y - 160) * .24} x2={x - (x - 160) * inset} y2={y - (y - 160) * inset} markerEnd={outgoing ? `url(#${marker})` : undefined} markerStart={incoming ? `url(#${marker})` : undefined} /></g>;
    })}
    <circle className="graph-self" cx="160" cy="160" r="24" /><text x="160" y="166" textAnchor="middle">나</text>
  </svg>{points.map(({ person, x, y }) => <button type="button" key={person.id} className="network-node" style={{ left: `${x / 3.2}%`, top: `${y / 3.2}%` }} aria-label={`${person.nickname} 연결 정보 보기`} aria-pressed={selectedId === person.id} title={person.nickname} onClick={() => setSelectedId(person.id)}><ProfileAvatar name={person.nickname} /><small>{person.nickname}</small></button>)}</div>
    <figcaption><p>화살표는 프로필을 열어본 방향이에요.</p><ul className="network-legend"><li>내가 봄 →</li><li>나를 봄 ←</li><li>서로 봄 ↔</li></ul><p className="result-footnote">그래프 {points.length}명 · 목록 {graph.nodes.length}명{graph.truncated ? ' 이상' : ''}</p></figcaption>
  </figure>{!mobile && desktopSelection && <aside className="network-selection" aria-label="선택한 동료"><Selection person={desktopSelection} /></aside>}
  {mobile && <Dialog className="network-profile-sheet" open={!!selected} title={selected?.nickname || '프로필로 이어진 동료'} hideTitle onClose={() => setSelectedId(null)}>{selected && <Selection person={selected} />}</Dialog>}</div>;
}
