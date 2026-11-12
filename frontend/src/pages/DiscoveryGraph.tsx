import { Icon } from '../components/Icon';
import { useEffect, useRef, useState } from 'react';
import { ParticipantPage } from '../components/ParticipantPage';
import { Link, navigate, useLocation } from '../app/navigation';
import { Button } from '../components/ui';
import { ApiError } from '../features/auth/api';
import { discoveryApi } from '../features/discovery/api';
import type { Graph, Stats } from '../features/discovery/api';
import { DiscoveryNav, PersonRow, VisitStats } from '../features/discovery/components';
import { NetworkFigure } from '../features/discovery/NetworkFigure';
export function DiscoveryGraphPage() {
  const location=useLocation(); const relationship=new URLSearchParams(location.split('?')[1]).get('relationship') || 'all';
  const [graph, setGraph] = useState<Graph | null>(null); const [stats, setStats] = useState<Stats | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [profileRequired, setProfileRequired] = useState(false); const generation = useRef(0);
  const [more, setMore] = useState(false);
  const offset = useRef(0);
  async function nextPage() {
    if (!graph || more) return;
    const version = generation.current;
    setMore(true); setError('');
    try {
      const page = await discoveryApi.graph(offset.current,relationship);
      if (generation.current !== version) return;
      offset.current += page.nodes.length;
      const ids = new Set(graph.nodes.map(node => node.id));
      setGraph({ ...graph, nodes: [...graph.nodes, ...page.nodes.filter(p => !ids.has(p.id))],
        edges: [...graph.edges,...page.edges], truncated: page.truncated && page.nodes.length > 0 });
    } catch (failure) {
      if (generation.current === version) {
        setGraph(null); setStats(null);
        setError(failure instanceof Error ? failure.message : '관계를 불러오지 못했어요.');
      }
    } finally { if (version === generation.current) setMore(false); }
  }
  useEffect(() => { void load(); return () => { generation.current++; }; }, [relationship]);
  async function load() {
    const version = ++generation.current; setMore(false); setLoading(true); setGraph(null); setStats(null); setError(''); setProfileRequired(false);
    try { const [next, counts] = await Promise.all([discoveryApi.graph(0,relationship), discoveryApi.stats()]); if (version === generation.current) { offset.current = next.nodes.length; setGraph(next); setStats(counts); } }
    catch (failure) { if (version === generation.current) { setError(failure instanceof Error ? failure.message : '발견 관계를 불러오지 못했어요.'); setProfileRequired(failure instanceof ApiError && failure.code === 'PROFILE_REQUIRED'); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  return <ParticipantPage page="discovery" className="discovery-page" busy={loading}><DiscoveryNav selected="graph" />{stats && <VisitStats stats={stats} />}{loading && <div className="discovery-message is-loading" role="status"><Icon name="clock" size={20} /><p>내 공개 열람 관계를 확인하고 있어요.</p></div>}
    {graph && <><div className="content-section-heading"><h2>프로필로 이어진 동료</h2><Button variant="quiet" onClick={load}>관계 새로고침</Button></div><p>서로의 프로필을 둘러본 기록이에요.</p><div className="relationship-filters" role="group" aria-label="프로필 방문 관계">{[['all','전체'],['visited','내가 봄'],['visitors','나를 봄'],['mutual','서로 봄']].map(([value,label]) => <Button key={value} variant="quiet" aria-pressed={relationship===value} onClick={() => navigate(`/discovery/graph?relationship=${value}`)}>{label}</Button>)}</div>{!graph.nodes.length ? <div className="discovery-message is-empty"><h3>첫 동료를 만나보세요</h3><p>아직 공개된 열람 관계가 없어요.</p><Link className="button button--primary" href="/discovery">동료 프로필 둘러보기</Link></div> : <><div className="discovery-relationships"><NetworkFigure key={relationship} graph={graph} /><ul className="people-list">{graph.nodes.map(p => <li key={p.id}><PersonRow person={p} /></li>)}</ul></div>{graph.truncated && <Button variant="secondary" disabled={more} onClick={nextPage}>{more ? '불러오는 중…' : '연결된 동료 더 보기'}</Button>}</> }</>}
    {error && <div className="discovery-message is-error"><p role="alert" className="form-notice is-error">{error}</p>{profileRequired ? <Link className="button button--primary" href="/me/profile">내 프로필과 참여 안내 확인하기</Link> : <Button variant="secondary" onClick={load}>발견 관계 다시 불러오기</Button>}</div>}
  </ParticipantPage>;
}
