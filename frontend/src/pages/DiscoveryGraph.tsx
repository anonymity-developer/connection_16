import { useEffect, useRef, useState } from 'react';
import { Link } from '../app/navigation';
import { Button } from '../components/ui';
import { ApiError } from '../features/auth/api';
import { discoveryApi } from '../features/discovery/api';
import type { Graph, Stats } from '../features/discovery/api';
import { DiscoveryNav, PersonRow, VisitStats } from '../features/discovery/components';
import { NetworkFigure } from '../features/discovery/NetworkFigure';
export function DiscoveryGraphPage() {
  const [graph, setGraph] = useState<Graph | null>(null); const [stats, setStats] = useState<Stats | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [profileRequired, setProfileRequired] = useState(false); const generation = useRef(0);
  useEffect(() => { void load(); return () => { generation.current++; }; }, []);
  async function load() {
    const version = ++generation.current; setLoading(true); setGraph(null); setStats(null); setError(''); setProfileRequired(false);
    try { const [next, counts] = await Promise.all([discoveryApi.graph(), discoveryApi.stats()]); if (version === generation.current) { setGraph(next); setStats(counts); } }
    catch (failure) { if (version === generation.current) { setError(failure instanceof Error ? failure.message : '발견 관계를 불러오지 못했어요.'); setProfileRequired(failure instanceof ApiError && failure.code === 'PROFILE_REQUIRED'); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  return <div className="page discovery-page"><div className="page-heading"><p className="context-label">TAG ME 네트워크</p><h1>나의 발견 관계</h1><p>프로필을 열어보며 이어진 동료를 확인해요.</p></div><DiscoveryNav selected="graph" />{stats && <VisitStats stats={stats} />}{loading && <p role="status">내 공개 열람 관계를 확인하고 있어요.</p>}
    {graph && <><div className="content-section-heading"><h2>나와 이어진 동료</h2><Button variant="quiet" onClick={load}>관계 새로고침</Button></div>{!graph.nodes.length ? <div className="discovery-message"><p>아직 공개된 열람 관계가 없어요.</p><Link className="button button--primary" href="/discovery">동료 프로필 둘러보기</Link></div> : <><div className="discovery-relationships"><NetworkFigure graph={graph} /><ul className="people-list">{graph.nodes.map(p => <li key={p.id}><PersonRow person={p} /></li>)}</ul></div>{graph.truncated && <p className="result-footnote">표시 범위보다 연결된 동료가 많아요. 전체 동료 목록에서 더 찾아보세요.</p>}</> }</>}
    {error && <div className="discovery-message"><p role="alert" className="form-notice is-error">{error}</p>{profileRequired ? <Link className="button button--primary" href="/me/profile">내 프로필과 참여 안내 확인하기</Link> : <Button variant="secondary" onClick={load}>발견 관계 다시 불러오기</Button>}</div>}
  </div>;
}
