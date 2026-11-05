import { useEffect, useId, useRef, useState } from 'react';
import { Link } from '../app/navigation';
import { Button } from '../components/ui';
import { ApiError } from '../features/auth/api';
import { discoveryApi } from '../features/discovery/api';
import type { Graph, Stats } from '../features/discovery/api';
import { DiscoveryNav, PersonRow, VisitStats } from '../features/discovery/components';
export function DiscoveryGraphPage() {
  const [graph, setGraph] = useState<Graph | null>(null); const [stats, setStats] = useState<Stats | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [profileRequired, setProfileRequired] = useState(false); const generation = useRef(0); const marker = `visit-arrow-${useId().replaceAll(':', '')}`;
  useEffect(() => { void load(); return () => { generation.current++; }; }, []);
  async function load() {
    const version = ++generation.current; setLoading(true); setGraph(null); setStats(null); setError(''); setProfileRequired(false);
    try { const [next, counts] = await Promise.all([discoveryApi.graph(), discoveryApi.stats()]); if (version === generation.current) { setGraph(next); setStats(counts); } }
    catch (failure) { if (version === generation.current) { setError(failure instanceof Error ? failure.message : '발견 관계를 불러오지 못했어요.'); setProfileRequired(failure instanceof ApiError && failure.code === 'PROFILE_REQUIRED'); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  const drawn = graph?.nodes.slice(0, 8) || []; const points = drawn.map((p, index) => ({ p, x: 160 + 112 * Math.cos(-Math.PI / 2 + index * 2 * Math.PI / drawn.length), y: 160 + 112 * Math.sin(-Math.PI / 2 + index * 2 * Math.PI / drawn.length) }));
  return <div className="page discovery-page"><div className="page-heading"><p className="context-label">TAG ME 네트워크</p><h1>나의 발견 관계</h1><p>프로필을 열어보며 이어진 동료를 확인해요.</p></div><DiscoveryNav selected="graph" />{stats && <VisitStats stats={stats} />}{loading && <p role="status">내 공개 열람 관계를 확인하고 있어요.</p>}
    {graph && <><div className="content-section-heading"><h2>나와 이어진 동료</h2><Button variant="quiet" onClick={load}>관계 새로고침</Button></div>{!graph.nodes.length ? <div className="discovery-message"><p>아직 공개된 열람 관계가 없어요.</p><Link className="button button--primary" href="/discovery">동료 프로필 둘러보기</Link></div> : <><div className="discovery-relationships"><figure className="discovery-graph"><svg viewBox="0 0 320 320" aria-hidden="true"><defs><marker id={marker} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0 0L10 5L0 10Z" /></marker></defs>{points.map(({ p, x, y }) => <g key={p.id} className={p.mutual ? 'is-mutual' : ''}><line x1={160 + (x - 160) * 0.24} y1={160 + (y - 160) * 0.24} x2={x - (x - 160) * 0.08} y2={y - (y - 160) * 0.08} markerEnd={graph.edges.some(e => e.from === graph.self.id && e.to === p.id) ? `url(#${marker})` : undefined} markerStart={graph.edges.some(e => e.from === p.id && e.to === graph.self.id) ? `url(#${marker})` : undefined} /><circle cx={x} cy={y} r="6" /><text x={x} y={y < 160 ? y - 16 : y + 24} textAnchor="middle">{Array.from(p.nickname).slice(0, 4).join('')}{Array.from(p.nickname).length > 4 ? '…' : ''}</text></g>)}<circle className="graph-self" cx="160" cy="160" r="24" /><text x="160" y="166" textAnchor="middle" className="graph-self-label">나</text></svg><figcaption>화살표는 프로필을 연 방향이에요. 그림은 최대 8명, 아래 목록은 최대 30명을 보여줘요.</figcaption></figure><ul className="people-list">{graph.nodes.map(p => <li key={p.id}><PersonRow person={p} /></li>)}</ul></div>{graph.truncated && <p className="result-footnote">표시 범위보다 연결된 동료가 많아요. 전체 동료 목록에서 더 찾아보세요.</p>}</> }</>}
    {error && <div className="discovery-message"><p role="alert" className="form-notice is-error">{error}</p>{profileRequired ? <Link className="button button--primary" href="/me/profile">내 프로필과 동의 확인하기</Link> : <Button variant="secondary" onClick={load}>발견 관계 다시 불러오기</Button>}</div>}
  </div>;
}
