import { TagPage } from './pages/Tag';
import { ProfilePage } from './pages/Profile';
import { useParticipation } from './features/participation/context';
import { useEffect, useState } from 'react';
import { Link, useLocation } from './app/navigation';
import { Layout } from './components/Layout';
import { Icon } from './components/Icon';
import { EmptyState } from './components/ui';
import { publicScreen, missions } from './features/missions/model';
import type { ScreenData } from './features/missions/model';
import { Account } from './pages/Account';
import { useAuth } from './features/auth/context';
import { Home } from './pages/Home';
import { Auth } from './pages/Auth';
import { MissionPage } from './pages/Mission';

export default function App() {
  const auth = useAuth();
  const participation = useParticipation();
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
    const title = path === '/auth/signup' ? '회원가입' : path === '/auth/password-reset' ? '비밀번호 재설정' : path === '/me/password' ? '비밀번호 변경' : path === '/auth' ? '로그인' : path.startsWith('/missions') ? '미션 안내' : path.startsWith('/discovery') ? '동료 찾기' : path === '/me/profile' ? '나의 프로필' : path === '/me' ? '마이페이지' : '미션 모으기';
    document.title = `${title} · TAG ME`;
    document.getElementById('main-content')?.focus({ preventScroll: true });
  }, [path]);

  const data: ScreenData = screen.preview ? screen : participation.collection && auth.user ? {
    preview: false, serverLoaded: true, nickname: auth.user.nickname,
    progress: Object.fromEntries(participation.collection.missions.map(item => [item.code, item.state === 'IN_PROGRESS' ? 'UNLOCKED' : item.state])) as ScreenData['progress'],
  } : publicScreen;
  let page;
  const mission = missions.find((item) => path === `/missions/${item.slug}`);
  if (path === '/') page = <Home data={data} />;
  else if (path === '/auth') page = <Auth key="login" mode="login" preview={screen.preview} />;
  else if (path === '/auth/signup') page = <Auth key="signup" mode="signup" preview={screen.preview} />;
  else if (path === '/auth/password-reset') page = <Auth key="reset" mode="reset" preview={screen.preview} />;
  else if (path === '/me/password' && (screen.preview || auth.user)) page = <Auth key="change" mode="change" preview={screen.preview} />;
  else if (path === '/me/profile') page = auth.user ? <ProfilePage /> : <Account preview={false} />;
  else if (mission) page = <MissionPage key={`${mission.code}:${data.preview ? 'preview' : 'server'}`} mission={mission} data={data} />;
  else if (path === '/discovery' || path === '/discovery/graph') page = <div className="page secondary-page"><div className="page-heading"><p className="context-label">TAG ME 네트워크</p><h1>동료 찾기</h1><p>관심사와 취향으로 새로운 동료를 발견해요.</p></div><EmptyState title="동료 탐색을 준비하고 있어요" action={<Link href="/" className="button button--secondary">미션 둘러보기<Icon name="arrow" size={17} /></Link>}>참여가 열리면 공개된 프로필을 바탕으로 동료를 찾을 수 있어요. 지금은 프로필이나 관계 기록이 표시되지 않아요.</EmptyState></div>;
  else if (path === '/me' || path === '/me/password') page = <Account preview={screen.preview} />;
  else if (path.startsWith('/t/')) page = <TagPage key={path} token={path.slice(3)} />;
  else if (path === '/complete' && (screen.preview && Object.values(screen.progress).every((state) => state === 'COMPLETED') || participation.collection?.completion.complete)) page = <div className="page secondary-page completion-page"><p className="context-label">{screen.preview ? '개발용 완성 화면' : '나의 수집 기록'}</p><h1>다섯 글자를 모두 모았어요</h1><div className="completion-letters" aria-label="SSAFY">{missions.map((item) => <span key={item.code}>{item.letter}</span>)}</div><p>{screen.preview ? '전체 완성 화면의 미리보기예요. 실제 기록은 생성되지 않아요.' : `운영상 완료 순번 ${participation.collection?.completion.completionOrder}번으로 기록됐어요. 수상 순위는 아직 확정되지 않았어요.`}</p><Link href="/" className="button button--primary">수집판 돌아가기<Icon name="arrow" /></Link></div>;
  else page = <div className="page secondary-page"><div className="page-heading"><h1>페이지를 찾을 수 없어요</h1></div><EmptyState title="주소를 다시 확인해 주세요" action={<Link href="/" className="button button--primary">홈으로 돌아가기<Icon name="arrow" /></Link>}>입력한 주소의 화면이 없어요. 홈에서 미션을 찾아보세요.</EmptyState></div>;

  return <><Layout path={path}>{auth.flash && !path.startsWith('/auth') && <p className="session-flash" role="status">{auth.flash}<button className="text-button" onClick={() => auth.setFlash('')} aria-label="안내 닫기">닫기</button></p>}{page}</Layout>{import.meta.env.DEV && screen.preview && <aside className="dev-preview" aria-label="개발용 상태 미리보기"><span><b>UI PREVIEW</b> · 실제 인증/DM/저장 없음</span><div>{['locked', 'unlocked', 'pending', 'complete'].map((mode) => <Link key={mode} href={`/?preview=${mode}`} aria-current={previewMode === mode ? 'page' : undefined}>{mode}</Link>)}</div></aside>}</>;
}
