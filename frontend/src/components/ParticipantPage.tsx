import type { ReactNode } from 'react';
import { Link } from '../app/navigation';
import { Icon } from './Icon';
import { EmptyState } from './ui';
import '../styles/participant-layout.css';

const headings = {
  missions: { context: 'TAG ME 미션', title: '미션 모으기', description: 'NFC 태그로 글자를 해금하고, 미션을 완료해서 글자를 획득해보세요' },
  discovery: { context: 'TAG ME 네트워킹', title: '동료 찾기', description: 'SSAFY 16기 서울 캠퍼스 동료들의 프로필을 구경해요' },
  account: { context: 'TAG ME 계정', title: '마이페이지', description: '나의 계정과 미션 상태를 확인해요.' },
} as const;
type PageKind = keyof typeof headings;

export function ParticipantHeading({ page }: { page: PageKind }) {
  const { context, title, description } = headings[page];
  return <div className="page-heading"><p className="context-label">{context}</p><h1>{title}</h1><p>{description}</p></div>;
}
export function ParticipantPage({ page, className = '', busy, children }: { page: PageKind; className?: string; busy?: boolean; children: ReactNode }) {
  return <div className={`page participant-page ${className}`} aria-busy={busy}><ParticipantHeading page={page} />{children}</div>;
}
export function ParticipantSignIn({ title, children }: { title: string; children: ReactNode }) {
  return <EmptyState className="participant-signin-card" icon="person" title={title} action={<Link href="/auth" className="button button--primary">로그인하기<Icon name="arrow" size={17} /></Link>}>{children}</EmptyState>;
}
export function ParticipantSectionHeading({ id, title, children }: { id: string; title: string; children?: ReactNode }) {
  return <div className="section-heading participant-section-heading"><h2 id={id}>{title}</h2>{children}</div>;
}
