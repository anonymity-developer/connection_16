import { useEffect, useId, useRef, useState } from 'react';
import { Link } from '../../app/navigation';
import type { Graph } from './api';
import { relationshipPresentation } from './relationship';

import { networkLayout } from './layout';

export function NetworkFigure({ graph }: { graph: Graph }) {
  const marker = `visit-arrow-${useId().replaceAll(':', '')}`;
  const canvas = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(280);
  useEffect(() => {
    const element = canvas.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width > 0) setWidth(entry.contentRect.width);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const points = networkLayout(graph, width);
  return <figure className="discovery-graph"><div ref={canvas} className="network-canvas"><svg viewBox="0 0 320 320" aria-hidden="true">
    <defs><marker id={marker} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="4" markerHeight="4" orient="auto-start-reverse"><path d="M0 0L10 5L0 10Z" /></marker></defs>
    {points.map(({ person, x, y, weight, incoming, outgoing }) => {
      const presentation = relationshipPresentation(weight);
      return <g key={person.id} className={`network-${presentation.strength}`}><line style={{ strokeWidth: presentation.width }} x1={160 + (x - 160) * 0.24} y1={160 + (y - 160) * 0.24} x2={x - (x - 160) * 0.12} y2={y - (y - 160) * 0.12} markerEnd={outgoing ? `url(#${marker})` : undefined} markerStart={incoming ? `url(#${marker})` : undefined} /></g>;
    })}
    <circle className="graph-self" cx="160" cy="160" r="24" /><text x="160" y="166" textAnchor="middle">나</text>
  </svg>{points.map(({ person, x, y }) => <Link key={person.id} className="network-node" style={{ left: `${x / 3.2}%`, top: `${y / 3.2}%` }} href={`/discovery/profiles/${person.id}`} aria-label={`${person.nickname} 그래프에서 프로필 보기`} title={person.nickname}><span aria-hidden="true">{Array.from(person.nickname)[0]}</span><small>{Array.from(person.nickname).slice(0, 4).join('')}{Array.from(person.nickname).length > 4 ? '…' : ''}</small></Link>)}</div>
    <figcaption><p>화살표는 프로필을 연 방향이에요. 선이 굵고 진할수록 교류가 많아요.</p><details className="network-explainer"><summary>관계 표시 기준 알아보기</summary><p>최초 방문·상호 방문·내가 누른 공개 응원 하트를 반영해요. 익명 활동은 제외해요. 화면 너비에 맞춰 관계가 강한 동료 {points.length}명을 표시해요. 전체 이름은 목록에서 확인할 수 있어요.</p></details></figcaption>
  </figure>;
}
