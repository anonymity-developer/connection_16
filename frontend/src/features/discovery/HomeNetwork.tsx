import { useEffect, useState } from 'react';
import { Link } from '../../app/navigation';
import { discoveryApi } from './api';
import type { Graph } from './api';
import { NetworkFigure } from './NetworkFigure';
export function HomeNetwork() {
  const [graph, setGraph] = useState<Graph | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    discoveryApi.graph().then(value => { if (active) setGraph(value); }).catch(() => { if (active) setError(true); });
    return () => { active = false; };
  }, [attempt]);
  return <section className="home-network" aria-labelledby="home-network-heading"><div className="section-heading"><h2 id="home-network-heading">나의 발견 관계</h2><Link href="/discovery/graph">전체 관계 보기</Link></div>
    {error ? <div role="alert"><p>관계를 불러오지 못했어요.</p><button className="text-button" onClick={() => { setError(false); setAttempt(value => value + 1); }}>다시 불러오기</button></div> : graph ? graph.nodes.length ? <NetworkFigure graph={graph} /> : <p>첫 연결을 만들어 보세요. <Link href="/discovery">동료 둘러보기</Link></p> : <p role="status">관계를 불러오고 있어요.</p>}
  </section>;
}
