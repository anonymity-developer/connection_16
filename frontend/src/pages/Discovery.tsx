import { useEffect, useRef, useState } from 'react';
import { Link, navigate, useLocation } from '../app/navigation';
import { Button } from '../components/ui';
import { ApiError } from '../features/auth/api';
import { participationApi } from '../features/participation/api';
import type { Interest } from '../features/participation/api';
import { discoveryApi } from '../features/discovery/api';
import type { People, Stats } from '../features/discovery/api';
import { DiscoveryNav, PersonRow, VisitStats } from '../features/discovery/components';
export function DiscoveryPage() {
  const location = useLocation(); const query = new URLSearchParams(location.split('?')[1]); const all = query.get('view') === 'people';
  const [search, setSearch] = useState(query.get('q') || ''); const [interest, setInterest] = useState(query.get('interestId') || '');
  const [people, setPeople] = useState<People | null>(null); const [stats, setStats] = useState<Stats | null>(null); const [options, setOptions] = useState<Interest[]>([]);
  const [loading, setLoading] = useState(true); const [more, setMore] = useState(false); const [error, setError] = useState(''); const [profileRequired, setProfileRequired] = useState(false); const generation = useRef(0);
  useEffect(() => { setSearch(new URLSearchParams(location.split('?')[1]).get('q') || ''); setInterest(new URLSearchParams(location.split('?')[1]).get('interestId') || ''); void load(); return () => { generation.current++; }; }, [location]);
  async function load() {
    const version = ++generation.current; setLoading(true); setMore(false); setPeople(null); setStats(null); setError(''); setProfileRequired(false);
    try { const [rows, counts, interests] = await Promise.all([all ? discoveryApi.people(query) : discoveryApi.recommended().then(items => ({ items, nextCursor: null })), discoveryApi.stats(), participationApi.interests()]); if (version === generation.current) { setPeople(rows); setStats(counts); setOptions(interests); } }
    catch (failure) { if (version === generation.current) { setError(failure instanceof Error ? failure.message : '동료 목록을 확인하지 못했어요.'); setProfileRequired(failure instanceof ApiError && failure.code === 'PROFILE_REQUIRED'); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  async function next() {
    if (!people?.nextCursor || more) return; const version = generation.current; setMore(true); setError('');
    try { const next = await discoveryApi.people(query, people.nextCursor); if (version === generation.current) setPeople(old => ({ items: [...(old?.items || []), ...next.items.filter(item => !old?.items.some(p => p.id === item.id))], nextCursor: next.nextCursor })); }
    catch (failure) { if (version === generation.current) setError(failure instanceof Error ? failure.message : '다음 동료를 확인하지 못했어요.'); }
    finally { if (version === generation.current) setMore(false); }
  }
  return <div className="page discovery-page"><div className="page-heading"><p className="context-label">TAG ME 네트워크</p><h1>동료 찾기</h1><p>관심사와 취향에서 대화의 시작을 찾아요.</p></div><DiscoveryNav selected={all ? 'people' : 'recommended'} />
    {stats && <VisitStats stats={stats} />}
    {all && <form className="people-filters" onSubmit={event => { event.preventDefault(); const params = new URLSearchParams({ view: 'people' }); if (search.trim()) params.set('q', search.trim()); if (interest) params.set('interestId', interest); navigate(`/discovery?${params}`); }}><div><label htmlFor="people-search">닉네임 검색</label><input id="people-search" maxLength={30} value={search} onChange={e => setSearch(e.target.value)} placeholder="닉네임을 입력해 주세요" /></div><div><label htmlFor="people-interest">관심사</label><select id="people-interest" value={interest} onChange={e => setInterest(e.target.value)}><option value="">전체 관심사</option>{options.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}</select></div><Button type="submit" disabled={loading}>동료 검색하기</Button></form>}
    {loading && <p className="discovery-message" role="status">공개된 동료와 내 열람 현황을 확인하고 있어요.</p>}
    {people && <section aria-labelledby="people-heading"><div className="content-section-heading"><h2 id="people-heading">{all ? '함께하는 동료' : '대화를 시작할 동료'}</h2><Button variant="quiet" disabled={loading} onClick={load}>목록 새로고침</Button></div>{!all && <p className="result-footnote">아직 열어보지 않은 프로필을 먼저, 공통 관심사와 같은 취향 유형을 참고해 보여드려요.</p>}{!people.items.length && <div className="discovery-message"><p>{all ? '조건에 맞는 공개 동료가 없어요.' : '지금 추천할 공개 동료가 없어요.'}</p><Link className="text-link" href="/discovery?view=people">전체 동료 보기</Link></div>}<ul className="people-list">{people.items.map(person => <li key={person.id}><PersonRow person={person} recommendation={!all} /></li>)}</ul>{people.nextCursor && <Button variant="secondary" disabled={more} onClick={next}>{more ? '불러오는 중…' : '동료 더 보기'}</Button>}</section>}
    {error && <div className="discovery-message"><p role="alert" className="form-notice is-error">{error}</p>{profileRequired ? <Link className="button button--primary" href="/me/profile">내 프로필과 동의 확인하기</Link> : <Button variant="secondary" onClick={load}>동료 목록 다시 불러오기</Button>}</div>}
  </div>;
}
