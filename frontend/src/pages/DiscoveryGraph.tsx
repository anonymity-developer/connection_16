import { Icon } from '../components/Icon';
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
  const [more, setMore] = useState(false);
  const offset = useRef(0);
  async function nextPage() {
    if (!graph || more) return;
    const version = generation.current;
    setMore(true); setError('');
    try {
      const page = await discoveryApi.graph(offset.current);
      if (generation.current !== version) return;
      offset.current += page.nodes.length;
      const ids = new Set(graph.nodes.map(node => node.id));
      setGraph({ ...graph, nodes: [...graph.nodes, ...page.nodes.filter(p => !ids.has(p.id))],
        truncated: page.truncated && page.nodes.length > 0 });
    } catch (failure) {
      if (generation.current === version) {
        setGraph(null); setStats(null);
        setError(failure instanceof Error ? failure.message : '관계를 불러오지 못했어요.');
      }
    } finally { if (version === generation.current) setMore(false); }
  }
  useEffect(() => { void load(); return () => { generation.current++; }; }, []);
  async function load() {
    const version = ++generation.current; setMore(false); setLoading(true); setGraph(null); setStats(null); setError(''); setProfileRequired(false);
    try { const [next, counts] = await Promise.all([discoveryApi.graph(), discoveryApi.stats()]); if (version === generation.current) { offset.current = next.nodes.length; setGraph(next); setStats(counts); } }
    catch (failure) { if (version === generation.current) { setError(failure instanceof Error ? failure.message : '발견 관계를 불러오지 못했어요.'); setProfileRequired(failure instanceof ApiError && failure.code === 'PROFILE_REQUIRED'); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  return <div className="page discovery-page participant-page" aria-busy={loading}><div className="page-heading"><p className="context-label">TAG ME 네트워크</p><h1>나의 발견 관계</h1><p>우리가 서로에게 보낸 관심의 흔적이에요.</p></div><DiscoveryNav selected="graph" />{stats && <VisitStats stats={stats} />}{loading && <div className="discovery-message is-loading" role="status"><Icon name="clock" size={20} /><p>내 공개 열람 관계를 확인하고 있어요.</p></div>}
    {graph && <><div className="content-section-heading"><h2>나와 이어진 동료</h2><Button variant="quiet" onClick={load}>관계 새로고침</Button></div>{!graph.nodes.length ? <div className="discovery-message is-empty"><h3>첫 동료를 만나보세요</h3><p>아직 공개된 열람 관계가 없어요.</p><Link className="button button--primary" href="/discovery">동료 프로필 둘러보기</Link></div> : <><div className="discovery-relationships"><NetworkFigure graph={graph} /><ul className="people-list">{graph.nodes.map(p => <li key={p.id}><PersonRow person={p} /></li>)}</ul></div>{graph.truncated && <Button variant="secondary" disabled={more} onClick={nextPage}>{more ? '불러오는 중…' : '연결된 동료 더 보기'}</Button>}</> }</>}
    {error && <div className="discovery-message is-error"><p role="alert" className="form-notice is-error">{error}</p>{profileRequired ? <Link className="button button--primary" href="/me/profile">내 프로필과 참여 안내 확인하기</Link> : <Button variant="secondary" onClick={load}>발견 관계 다시 불러오기</Button>}</div>}
  </div>;
}
