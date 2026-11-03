import { Link } from '../app/navigation';
import { Icon } from './Icon';
import type { ReactNode } from 'react';

const links = [{ href: '/', name: '미션 모으기', icon: 'home' }, { href: '/discovery', name: '동료 찾기', icon: 'people' }, { href: '/me', name: '마이페이지', icon: 'person' }] as const;

export function Brand() {
  return <Link href="/" className="brand" aria-label="TAG ME 홈"><span className="brand-mark"><Icon name="tag" size={19} /></span>TAG ME<span className="brand-dot">®</span></Link>;
}

export function Layout({ path, children }: { path: string; children: ReactNode }) {
  return <>
    <a className="skip-link" href="#main-content">본문으로 바로가기</a>
    <header className="site-header"><div className="header-inner"><Brand /><nav className="desktop-nav" aria-label="주 메뉴">{links.map((link) => <Link key={link.href} href={link.href} aria-current={path === link.href ? 'page' : undefined}>{link.name}</Link>)}</nav><Link className="header-login" href="/auth">로그인<Icon name="arrow" size={16} /></Link></div></header>
    <main id="main-content" tabIndex={-1}>{children}</main>
    <footer className="site-footer"><Brand /><p>새로운 연결, 우리 사이의 작은 시작.</p><span>MADE FOR SSAFY 16TH</span></footer>
    <nav className="mobile-nav" aria-label="모바일 주 메뉴">{links.map((link) => <Link key={link.href} href={link.href} aria-current={path === link.href ? 'page' : undefined}><Icon name={link.icon} size={21} /><span>{link.name}</span></Link>)}</nav>
  </>;
}
