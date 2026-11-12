import '../styles/participant.css';
import '../styles/mission-feedback.css';
import { useAuth } from '../features/auth/context';
import { Link } from '../app/navigation';
import { Icon } from './Icon';
import { MissionRewardFeedback } from './MissionRewardFeedback';
import { ErrorReportButton } from './ErrorReportButton';
import type { ReactNode } from 'react';

const links = [{ href: '/', name: '미션 모으기', icon: 'home' }, { href: '/discovery', name: '동료 찾기', icon: 'people' }, { href: '/me', name: '마이페이지', icon: 'person' }] as const;
export function Brand() {
  return <Link href="/" className="brand" aria-label="TAG ME 홈"><Icon name="tag" size={23} />TAG ME</Link>;
}
export function Layout({ path, children }: { path: string; children: ReactNode }) {
  const auth = useAuth();
  if (path === '/admin' || path.startsWith('/admin/')) return <><a className="skip-link" href="#main-content">본문으로 바로가기</a><main id="main-content" tabIndex={-1}>{children}</main></>;
  const active = (href: string) => href === '/' ? path === '/' || path === '/missions' || path.startsWith('/missions/') || path === '/complete' : path === href || path.startsWith(`${href}/`);
  return <div className={path === '/admin' ? 'app-shell participant-shell operations-shell' : 'app-shell participant-shell'}>
    <a className="skip-link" href="#main-content">본문으로 바로가기</a>
    <header className="site-header"><div className="header-inner"><Brand /><span className="workspace-label">SSAFY 16기</span><nav className="desktop-nav" aria-label="주 메뉴">{links.map((link) => <Link key={link.href} href={link.href} aria-current={active(link.href) ? 'page' : undefined}>{link.name}</Link>)}</nav><div className="header-actions"><ErrorReportButton key={auth.user?.id??'guest'} path={path}/><Link className="header-login" href={auth.user ? '/me' : '/auth'} aria-current={path.startsWith('/auth') ? 'page' : undefined}>{auth.user ? '내 계정' : '로그인'}<Icon name="arrow" size={16} /></Link></div></div></header>
    <main id="main-content" tabIndex={-1}>{children}</main>
    <MissionRewardFeedback key={auth.user?.id ?? 'guest'} path={path} />
    <footer className="site-footer"><span>TAG ME · SSAFY 16기 네트워킹 이벤트</span><span>기획·제작 | 서울 지역대표 이다연 · 백지원</span></footer>
    <nav className="mobile-nav" aria-label="모바일 주 메뉴">{links.map((link) => <Link key={link.href} href={link.href} aria-current={active(link.href) ? 'page' : undefined}><Icon name={link.icon} size={21} /><span>{link.name}</span></Link>)}</nav>
  </div>;
}
