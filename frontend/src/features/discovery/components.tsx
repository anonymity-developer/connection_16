import { useId } from 'react';
import { Link } from '../../app/navigation';
import { ProfileAvatar } from '../../components/ProfileAvatar';
import { Icon } from '../../components/Icon';
import type { Person, Stats } from './api';
export function DiscoveryNav({ selected }: { selected: 'recommended' | 'people' | 'graph' }) {
  return <nav className="discovery-tabs" aria-label="동료 탐색 메뉴"><Link href="/discovery" aria-current={selected === 'recommended' ? 'page' : undefined}>추천 동료</Link><Link href="/discovery?view=people" aria-current={selected === 'people' ? 'page' : undefined}>전체 동료</Link><Link href="/discovery/graph" aria-current={selected === 'graph' ? 'page' : undefined}>프로필로 이어진 동료</Link></nav>;
}
export function VisitStats({ stats }: { stats: Stats }) {
  return <dl className="visit-stats"><div><dt>내가 본 동료</dt><dd>{stats.visitedCount}<span>명</span></dd></div><div><dt>나를 본 동료</dt><dd>{stats.visitorCount}<span>명</span></dd></div><div><dt>서로 본 동료</dt><dd>{stats.mutualCount}<span>명</span></dd></div></dl>;
}
export function PersonRow({ person, recommendation = false }: { person: Person; recommendation?: boolean }) {
  const reasonId = useId();
  const relation = person.mutual ? '서로 열어봤어요' : person.visitedMe ? '내 프로필을 열어봤어요' : person.visited ? '내가 열어봤어요' : '';
  const reasons = [
    !person.visited ? '아직 열어보지 않은 프로필' : '',
    person.sharedInterests.length ? `공통 관심사: ${person.sharedInterests.join(', ')}` : '',
    person.sameType ? '같은 취향 유형' : '',
  ].filter(Boolean);
  const showReasons = recommendation && reasons.length > 0;
  return <Link className="person-row" data-card-tone={person.id % 3} href={`/discovery/profiles/${person.id}`} aria-label={`${person.nickname} 프로필 보기`} aria-describedby={showReasons ? reasonId : undefined}>
    <ProfileAvatar name={person.nickname} />
    <div className="person-summary">
      <strong className="person-name" title={person.nickname}>{person.nickname}</strong>
      {person.bio && <p className="person-bio">{person.bio}</p>}
      <p className="person-interests">{person.interests.map(i => i.name).join(' · ') || '관심사를 천천히 알아가요'}</p>
      {person.type && <p className="person-type">취향 유형 · {person.type.title}</p>}
      {showReasons && <p id={reasonId} className="person-reason" aria-label="추천 이유">{reasons.map(reason => <span className="reason-chip" key={reason}>{reason}</span>)}</p>}
      {relation && <span className={`person-relation ${person.mutual ? 'is-mutual' : ''}`}>{relation}</span>}
    </div>
    <Icon name="arrow" size={18} />
  </Link>;
}
