import { Link } from '../app/navigation';
import { Icon } from './Icon';
import type { ReactNode } from 'react';

const links = [{ href: '/', name: '미션 모으기', icon: 'home' }, { href: '/discovery', name: '동료 찾기', icon: 'people' }, { href: '/me', name: '마이페이지', icon: 'person' }] as const;
export function Brand() {
  return <Link href="/" className="brand" aria-label="TAG ME 홈"><Icon name="tag" size={23} />TAG ME</Link>;
}
export function Layout({ path, children }: { path: string; children: ReactNode }) {
  const active = (href: string) => href === '/' ? path === '/' || path.startsWith('/missions/') || path === '/complete' : path === href || path.startsWith(`${href}/`);
  return <>
    <a className="skip-link" href="#main-content">본문으로 바로가기</a>
    <header className="site-header"><div className="header-inner"><Brand /><span className="workspace-label">SSAFY 16기</span><nav className="desktop-nav" aria-label="주 메뉴">{links.map((link) => <Link key={link.href} href={link.href} aria-current={active(link.href) ? 'page' : undefined}>{link.name}</Link>)}</nav><Link className="header-login" href="/auth" aria-current={path.startsWith('/auth') ? 'page' : undefined}>로그인<Icon name="arrow" size={16} /></Link></div></header>
    <main id="main-content" tabIndex={-1}>{children}</main>
    <footer className="site-footer"><span>TAG ME · SSAFY 16기 네트워킹</span><Link href="/">미션 안내로 돌아가기<Icon name="arrow" size={14} /></Link></footer>
    <nav className="mobile-nav" aria-label="모바일 주 메뉴">{links.map((link) => <Link key={link.href} href={link.href} aria-current={active(link.href) ? 'page' : undefined}><Icon name={link.icon} size={21} /><span>{link.name}</span></Link>)}</nav>
  </>;
}
