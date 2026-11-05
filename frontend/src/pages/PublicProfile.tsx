import { useEffect, useRef, useState } from 'react';
import { Link } from '../app/navigation';
import { Button } from '../components/ui';
import { Icon } from '../components/Icon';
import { ApiError } from '../features/auth/api';
import { discoveryApi } from '../features/discovery/api';
import type { Detail } from '../features/discovery/api';
export function PublicProfilePage({ id }: { id: number }) {
  const [detail, setDetail] = useState<Detail | null>(null); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [code, setCode] = useState(''); const generation = useRef(0);
  useEffect(() => { void load(); return () => { generation.current++; }; }, [id]);
  async function load() {
    const version = ++generation.current; setLoading(true); setDetail(null); setError(''); setCode('');
    try { const next = await discoveryApi.open(id); if (version === generation.current) setDetail(next); }
    catch (failure) { if (version === generation.current) { setError(failure instanceof Error ? failure.message : '프로필을 열지 못했어요.'); setCode(failure instanceof ApiError ? failure.code : ''); } }
    finally { if (version === generation.current) setLoading(false); }
  }
  const safe = (value: string | null) => { try { const url = new URL(value || ''); return url.protocol === 'https:' && !url.username && !url.password ? value : null; } catch { return null; } };
  return <div className="page public-profile-page"><Link className="back-link" href="/discovery"><Icon name="arrowLeft" size={16} />동료 찾기</Link><div className="page-heading"><p className="context-label">TAG ME 동료</p><h1>동료의 프로필</h1><p>공개된 소개에서 다음 대화를 시작해 보세요.</p></div>
    {loading && <p role="status">현재 공개 상태를 확인하고 프로필을 열고 있어요.</p>}
    {error && <div className="discovery-message"><p role="alert" className="form-notice is-error">{error}</p>{code === 'PROFILE_REQUIRED' ? <Link className="button button--primary" href="/me/profile">내 프로필과 동의 확인하기</Link> : code === 'PROFILE_NOT_FOUND' || code === 'EVENT_CLOSED' ? <Link className="button button--secondary" href="/discovery">동료 목록으로 돌아가기</Link> : <Button variant="secondary" onClick={load}>프로필 다시 열기</Button>}</div>}
    {detail && <article className="public-profile"><div className="public-profile-intro"><span className="person-initial is-large" aria-hidden="true">{Array.from(detail.person.nickname)[0]}</span><div><p className="public-nickname">{detail.person.nickname}</p><p className="person-relation">{detail.person.mutual ? '서로 프로필을 열어봤어요' : detail.person.visitedMe ? '내 프로필을 열어본 동료예요' : detail.newlyVisited ? '처음 열어본 프로필이에요' : detail.person.visited ? '이전에 열어본 프로필이에요' : '내 프로필이에요'}</p></div></div><dl className="public-profile-facts"><div><dt>이름</dt><dd>{detail.realName}</dd></div><div><dt>지역과 반</dt><dd>{detail.region} · {detail.classNo}반</dd></div></dl><section><h2>나를 소개하는 한마디</h2><p className="public-bio">{detail.bio}</p></section><section><h2>관심사와 취향</h2><p className="public-bio">{detail.person.interests.map(i => i.name).join(' · ')}</p>{detail.person.type && <p className="person-type">취향 유형 · {detail.person.type.title}</p>}</section><div className="public-profile-links">{safe(detail.githubUrl) && <a className="button button--secondary" href={safe(detail.githubUrl)!} target="_blank" rel="noopener noreferrer">GitHub 열기 (새 탭)<Icon name="arrow" size={16} /></a>}{safe(detail.blogUrl) && <a className="button button--secondary" href={safe(detail.blogUrl)!} target="_blank" rel="noopener noreferrer">블로그 열기 (새 탭)<Icon name="arrow" size={16} /></a>}</div><p className="result-footnote">이름·반·소개는 참여자가 직접 작성한 정보예요.</p><Link className="text-link" href="/discovery/graph">나의 발견 관계 보기<Icon name="arrow" size={16} /></Link></article>}
  </div>;
}
