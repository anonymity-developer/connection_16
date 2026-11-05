import { Link } from '../../app/navigation';
import { Icon } from '../../components/Icon';
import type { Person, Stats } from './api';
export function DiscoveryNav({ selected }: { selected: 'recommended' | 'people' | 'graph' }) {
  return <nav className="discovery-tabs" aria-label="동료 탐색 메뉴"><Link href="/discovery" aria-current={selected === 'recommended' ? 'page' : undefined}>추천 동료</Link><Link href="/discovery?view=people" aria-current={selected === 'people' ? 'page' : undefined}>전체 동료</Link><Link href="/discovery/graph" aria-current={selected === 'graph' ? 'page' : undefined}>나의 발견</Link></nav>;
}
export function VisitStats({ stats }: { stats: Stats }) {
  return <dl className="visit-stats"><div><dt>내가 열어본 프로필</dt><dd>{stats.visitedCount}<span>명</span></dd></div><div><dt>내 프로필을 열어본 동료</dt><dd>{stats.visitorCount}<span>명</span></dd></div><div><dt>서로 열어본 프로필</dt><dd>{stats.mutualCount}<span>명</span></dd></div></dl>;
}
export function PersonRow({ person, recommendation = false }: { person: Person; recommendation?: boolean }) {
  const relation = person.mutual ? '서로 열어봤어요' : person.visitedMe ? '내 프로필을 열어봤어요' : person.visited ? '내가 열어봤어요' : '';
  return <Link className="person-row" href={`/discovery/profiles/${person.id}`} aria-label={`${person.nickname} 프로필 보기`}><span className="person-initial" aria-hidden="true">{Array.from(person.nickname)[0]}</span><div className="person-summary"><strong className="person-name" title={person.nickname}>{person.nickname}</strong><p className="person-interests">{person.interests.map(i => i.name).join(' · ')}</p>{person.type && <p className="person-type">취향 유형 · {person.type.title}</p>}{recommendation && <p className="person-reason">{person.sharedInterests.length ? `공통 관심사: ${person.sharedInterests.join(', ')}` : person.sameType ? '같은 취향 유형이에요' : !person.visited ? '아직 프로필을 열어보지 않은 동료예요' : '이전에 열어본 프로필이에요'}</p>}{relation && <span className={`person-relation ${person.mutual ? 'is-mutual' : ''}`}>{relation}</span>}</div><Icon name="arrow" size={18} /></Link>;
}
