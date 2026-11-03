import { useEffect, useState } from 'react';
import { Link, useLocation } from './app/navigation';
import { Layout } from './components/Layout';
import { Icon } from './components/Icon';
import { EmptyState } from './components/ui';
import { publicScreen, missions } from './features/missions/model';
import type { ScreenData } from './features/missions/model';
import { Home } from './pages/Home';
import { Auth } from './pages/Auth';
import { MissionPage } from './pages/Mission';

export default function App() {
  const location = useLocation();
  const path = location.split('?')[0].replace(/\/$/, '') || '/';
  const [screen, setScreen] = useState<ScreenData>(publicScreen);
  const previewMode = import.meta.env.DEV ? new URLSearchParams(location.split('?')[1]).get('preview') : null;

  useEffect(() => {
    let active = true;
    if (import.meta.env.DEV && previewMode !== null) {
      import('./dev/fixtures').then(({ previewScreen }) => { if (active) setScreen(previewScreen(previewMode)); });
    } else setScreen(publicScreen);
    return () => { active = false; };
  }, [previewMode]);

  useEffect(() => {
    window.scrollTo(0, 0);
    document.title = path.startsWith('/auth') ? '로그인 · TAG ME' : path.startsWith('/missions') ? '다섯 미션 · TAG ME' : 'TAG ME — 우리 사이, 새로운 연결';
    document.getElementById('main-content')?.focus({ preventScroll: true });
  }, [path]);

  let page;
  const mission = missions.find((item) => path === `/missions/${item.slug}`);
  if (path === '/') page = <Home data={screen} />;
  else if (path === '/auth') page = <Auth key="login" mode="login" preview={screen.preview} />;
  else if (path === '/auth/signup') page = <Auth key="signup" mode="signup" preview={screen.preview} />;
  else if (path === '/auth/password-reset') page = <Auth key="reset" mode="reset" preview={screen.preview} />;
  else if (path === '/me/password' && screen.preview) page = <Auth key="change" mode="change" preview />;
  else if (mission) page = <MissionPage key={`${mission.code}:${screen.progress[mission.code]}`} mission={mission} data={screen} />;
  else if (path === '/discovery' || path === '/discovery/graph') page = <div className="page secondary-page"><span className="eyebrow">FIND YOUR PEOPLE</span><h1>이름 너머의<br /><span className="underlined">우리</span>를 만나요.</h1><p className="page-intro">관심사가 닿는 동료, 비슷한 결의 새로운 연결.</p><EmptyState title="첫 번째 연결을 기다리고 있어요." action={<Link href="/auth" className="button button--primary">로그인하고 준비하기<Icon name="arrow" size={17} /></Link>}>참여가 열리면 관심사와 취향으로 동료를 발견할 수 있어요.<br />서로의 프로필을 방문하며 우리 사이의 연결을 만들어가요.</EmptyState></div>;
  else if (path === '/me' || path === '/me/password') page = <div className="page secondary-page"><span className="eyebrow">MY CONNECTIONS</span><h1>나의 이야기가<br />시작되는 곳.</h1><EmptyState title="먼저, 우리 인사해요." action={<Link href="/auth" className="button button--primary">로그인하기<Icon name="arrow" size={17} /></Link>}>프로필과 미션 기록은 로그인 후 확인할 수 있어요.<br />아이디는 Mattermost와 같고, 비밀번호는 TAG ME에서 설정해요.</EmptyState></div>;
  else if (path.startsWith('/t/')) page = <div className="page secondary-page"><EmptyState title={/^[A-Za-z0-9_-]{43}$/.test(path.slice(3)) ? '새로운 발견이 기다리고 있어요.' : '태그 주소를 다시 확인해 주세요.'} action={<Link href="/auth" className="button button--primary">로그인 화면으로<Icon name="arrow" size={17} /></Link>}>지금은 참여 기능을 준비하고 있어요.<br />태그 주소만으로 미션이 해금되지는 않아요.</EmptyState></div>;
  else if (path === '/complete' && screen.preview && Object.values(screen.progress).every((state) => state === 'COMPLETED')) page = <div className="page secondary-page completion-page"><span className="eyebrow">FIVE DISCOVERIES, ONE CONNECTION</span><h1>우리의 연결이<br /><span className="underlined">완성됐어요!</span></h1><div className="completion-letters" aria-label="SSAFY">{missions.map((item) => <span key={item.code} className={`color-${item.color}`}>{item.letter}</span>)}</div><p>전체 완성 화면의 개발용 미리보기예요.<br />실제 완료 기록이나 수상 순위를 생성하지 않아요.</p><Link href="/discovery" className="button button--primary">새로운 동료 만나기<Icon name="arrow" /></Link></div>;
  else page = <div className="page secondary-page"><EmptyState title="아직 연결되지 않은 페이지예요." action={<Link href="/" className="button button--primary">홈으로 돌아가기<Icon name="arrow" /></Link>}>주소를 확인하거나 홈에서 새로운 미션을 만나보세요.</EmptyState></div>;

  return <><Layout path={path}>{page}</Layout>{import.meta.env.DEV && screen.preview && <aside className="dev-preview" aria-label="개발용 상태 미리보기"><span><b>UI PREVIEW</b> · 실제 인증/DM/저장 없음</span><div>{['locked', 'unlocked', 'pending', 'complete'].map((mode) => <Link key={mode} href={`/?preview=${mode}`} aria-current={previewMode === mode ? 'page' : undefined}>{mode}</Link>)}</div></aside>}</>;
}
