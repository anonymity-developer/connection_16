import type { ReactNode } from 'react';
import { Link } from '../app/navigation';
import { Icon } from './Icon';
import { EmptyState } from './ui';
import '../styles/participant-layout.css';

const headings = {
  missions: { context: 'TAG ME 미션', title: '미션 모으기', description: '곳곳의 NFC 태그를 찾아 미션을 완료하고, 새로운 동료를 만나보세요.' },
  discovery: { context: 'TAG ME 네트워크', title: '동료 찾기', description: '같은 관심사에서 대화를 시작해요.' },
  account: { context: 'TAG ME 계정', title: '마이페이지', description: '나의 계정과 참여 준비 상태를 확인해요.' },
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
