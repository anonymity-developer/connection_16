import { useEffect, useRef, useState } from 'react';
import '../styles/account.css';
import { ConnectionStickers } from '../components/EventArtwork';
import { LetterSticker } from '../components/LetterSticker';
import { Link, navigate } from '../app/navigation';
import { Button } from '../components/ui';
import { Icon } from '../components/Icon';
import { useAuth } from '../features/auth/context';
import { participationApi } from '../features/participation/api';
import type { TagInspection } from '../features/participation/api';
import { useParticipation } from '../features/participation/context';
import { missions } from '../features/missions/model';
import type { MissionCode } from '../features/missions/model';

const actions: Record<MissionCode, string> = { SHOW_ME: '프로필 완성하기', SYNC_ME: '취향 알아보기', MAKE_A_WISH: '소원 남기기', FAST_TAP: '탭 도전하기', SAY_SOMETHING: '응원 남기기' };
export function TagPage({ token }: { token: string }) {
  const auth = useAuth(), participation = useParticipation();
  const [busy, setBusy] = useState(false), [checking, setChecking] = useState(true), [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [inspection, setInspection] = useState<{ token: string; value: TagInspection } | null>(null);
  const opening = useRef(false), version = useRef(0);
  const valid = token === 'resume' || /^[A-Za-z0-9_-]{43}$/.test(token);
  const found = inspection?.token === token ? inspection.value : null;
  const mission = missions.find(m => m.code === found?.missionCode);
  useEffect(() => {
    const current = ++version.current;
    setError(''); setInspection(null); setChecking(valid); setBusy(false); opening.current = false;
    if (valid) void participationApi.inspectTag(token === 'resume' ? undefined : token).then(value => {
      if (!missions.some(m => m.code === value.missionCode && m.letter === value.letter)) throw new Error('태그의 미션을 확인하지 못했어요.');
      if (current === version.current) setInspection({ token, value });
    }).catch(failure => {
      if (current === version.current) setError(failure instanceof Error ? failure.message : '태그를 확인하지 못했어요.');
    }).finally(() => { if (current === version.current) setChecking(false); });
    return () => { version.current++; };
  }, [token, valid, retry]);
  async function open() {
    if (opening.current || !mission || !found) return;
    const current = version.current;
    opening.current = true; setBusy(true); setError('');
    try {
      if (!auth.user || auth.user.onboardingRequired) {
        if (token !== 'resume') await participationApi.rememberTag(token);
        if (current !== version.current) return;
        auth.rememberReturnPath(`/t/${token}`); navigate(auth.user ? '/me/profile' : '/auth'); return;
      }
      const result = token === 'resume' ? await participationApi.resume() : await participationApi.resolve(token);
      const expectedRoute = mission.code === 'SHOW_ME' ? '/me/profile' : `/missions/${mission.slug}`;
      if (current !== version.current) return;
      if (result.missionCode !== mission.code || result.route !== expectedRoute) throw new Error('이동할 미션을 확인하지 못했어요.');
      await participation.refresh();
      if (current !== version.current) return;
      auth.setFlash(result.newlyCompleted ? 'SHOW ME의 첫 번째 S를 모았어요.' : mission.code === 'SHOW_ME' ? '프로필에서 첫 번째 S의 준비 상태를 확인해 주세요.' : result.newlyUnlocked ? `${found.letter} 미션을 열었어요.` : '이미 열어 둔 미션이에요. 저장된 상태로 이어갈게요.');
      navigate(expectedRoute);
    } catch (failure) { if (current === version.current) setError(failure instanceof Error ? failure.message : '태그를 확인하지 못했어요.'); }
    finally { if (current === version.current) { opening.current = false; setBusy(false); } }
  }
  const letter = mission?.code === 'SHOW_ME' ? '소개 S' : mission?.code === 'SYNC_ME' ? '취향 S' : found?.letter;
  return <div className="page secondary-page tag-page account-theme">
    <div className="page-heading tag-welcome"><p className="context-label">NFC로 만나는 TAG ME</p><h1>{found ? `${letter} 미션을 찾았어요` : !valid ? '태그 주소를 다시 확인해 주세요' : checking ? '태그를 확인하고 있어요' : '태그를 확인하지 못했어요'}</h1></div>
    <section className="tag-discovery" aria-busy={checking || busy}>
      {found && mission ? <><div className="tag-discovery-artwork" key={token}><LetterSticker code={mission.code} collectionState="uncollected" /></div><h2>{found.title}</h2><p>{mission.description}</p><Button onClick={open} disabled={busy || !auth.ready}>{busy ? '미션 연결 중…' : !auth.user ? '로그인하고 이어가기' : auth.user.onboardingRequired ? '프로필 등록하고 이어가기' : actions[mission.code]}<Icon name="arrow" size={18} /></Button>{(!auth.user || auth.user.onboardingRequired) && <p className="tag-registration-note">처음이라면 로그인·가입과 프로필 등록 후 이 미션으로 이어져요.</p>}</> : <><ConnectionStickers className="tag-stickers" />{checking ? <p role="status">어떤 글자의 미션인지 확인하고 있어요.</p> : valid ? <Button variant="secondary" onClick={() => setRetry(value => value + 1)}>다시 확인하기</Button> : <p>현장의 NFC 태그를 다시 읽어주세요.</p>}</>}
    </section>
    {error && <p className="form-notice is-error tag-error" role="alert">{error}</p>}
    {valid && <p className="tag-footnote">로그인 전 연결은 같은 브라우저에서 15분 동안 이어갈 수 있어요. 만료되면 현장 태그를 다시 읽어주세요.</p>}
    <Link href="/" className="text-link">전체 미션 보기<Icon name="arrow" size={16} /></Link>
  </div>;
}
