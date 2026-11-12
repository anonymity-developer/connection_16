import { useEffect, useRef, useState } from 'react';
import { Link } from '../app/navigation';
import { Button } from '../components/ui';
import { ProfileAvatar } from '../components/ProfileAvatar';
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
  return <div className="page public-profile-page participant-page" aria-busy={loading}><Link className="back-link" href="/discovery"><Icon name="arrowLeft" size={16} />추천 결과로 돌아가기</Link><div className="page-heading"><p className="context-label">TAG ME 동료</p><h1>동료의 프로필</h1><p>짧은 소개 속에서, 우리 사이의 공통점을 찾아봐요.</p></div>
    {loading && <div className="discovery-message is-loading" role="status"><Icon name="clock" size={20} /><p>현재 공개 상태를 확인하고 프로필을 열고 있어요.</p></div>}
    {error && <div className="discovery-message is-error"><p role="alert" className="form-notice is-error">{error}</p>{code === 'PROFILE_REQUIRED' ? <Link className="button button--primary" href="/me/profile">내 프로필과 참여 안내 확인하기</Link> : code === 'PROFILE_NOT_FOUND' || code === 'EVENT_CLOSED' ? <Link className="button button--secondary" href="/discovery">동료 목록으로 돌아가기</Link> : <Button variant="secondary" onClick={load}>프로필 다시 열기</Button>}</div>}
    {detail && <article className="public-profile"><div className="public-profile-intro"><ProfileAvatar key={detail.photoUrl} name={detail.person.nickname} src={detail.photoUrl} className="is-large" /><div><p className="public-nickname">{detail.person.nickname}</p><p className="person-relation">{detail.person.mutual ? '서로 프로필을 열어봤어요' : detail.person.visitedMe ? '내 프로필을 열어본 동료예요' : detail.newlyVisited ? '처음 열어본 프로필이에요' : detail.person.visited ? '내가 열어봤어요' : '내 프로필이에요'}</p></div></div><dl className="public-profile-facts"><div><dt>이름</dt><dd>{detail.realName}</dd></div><div><dt>지역과 반</dt><dd>{detail.region} · {detail.classNo}반</dd></div></dl><section><h2>나를 소개하는 한마디</h2><p className="public-bio">{detail.bio || '아직 한 줄 소개를 작성하지 않았어요.'}</p></section><section><h2>관심사와 취향</h2><p className="public-bio">{detail.person.interests.length ? detail.person.interests.map(i => <span className="profile-interest-tag" key={i.id}>{i.name}</span>) : '아직 관심사를 선택하지 않았어요.'}</p>{detail.person.type && <p className="person-type">취향 유형 · {detail.person.type.title}</p>}</section><div className="public-profile-links">{safe(detail.githubUrl) && <a className="button button--secondary" href={safe(detail.githubUrl)!} target="_blank" rel="noopener noreferrer">GitHub 열기 (새 탭)<Icon name="arrow" size={16} /></a>}{safe(detail.blogUrl) && <a className="button button--secondary" href={safe(detail.blogUrl)!} target="_blank" rel="noopener noreferrer">블로그 열기 (새 탭)<Icon name="arrow" size={16} /></a>}</div><p className="result-footnote">이름·지역·반·사진은 Mattermost에서 가져왔고, 소개와 링크는 참여자가 작성했어요.</p><Link className="text-link" href="/discovery/graph">프로필로 이어진 동료<Icon name="arrow" size={16} /></Link></article>}
  </div>;
}
