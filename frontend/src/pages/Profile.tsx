import { useEffect, useRef, useState } from 'react';
import '../styles/account.css';
import { LetterSticker } from '../components/LetterSticker';
import { Link, navigate } from '../app/navigation';
import { Button, EmptyState, Field } from '../components/ui';
import { Icon } from '../components/Icon';
import { ProfileAvatar } from '../components/ProfileAvatar';
import { authApi, ApiError } from '../features/auth/api';
import { useAuth } from '../features/auth/context';
import { participationApi } from '../features/participation/api';
import type { EventState, Interest, ProfileState } from '../features/participation/api';
import { useParticipation } from '../features/participation/context';

import { InterestPicker } from '../features/participation/InterestPicker';
import { interestsComplete } from '../features/participation/catalog';
import { useMissionRuntime } from '../features/admin/MissionRuntime';

const disclosure = '목록에는 닉네임·한 줄 소개·관심사가 표시되며, 닉네임을 누르면 이름·지역·반·사진과 작성한 링크가 참여자에게 공개돼요.';
export function ProfilePage() {
  const runtime = useMissionRuntime(); const api = runtime?.participation ?? participationApi;
  const auth = useAuth(), participation = useParticipation();
  const [state, setState] = useState<ProfileState | null>(null), [interests, setInterests] = useState<Interest[]>([]), [schedule, setSchedule] = useState<EventState | null>(null);
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState(''), [conflict, setConflict] = useState(false);
  const [values, setValues] = useState({ nickname: '', bio: '', githubUrl: '', blogUrl: '', interestIds: [] as number[], accepted: false });
  const form = useRef<HTMLFormElement>(null), active = useRef(true);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, []);
  async function load() {
    setLoading(true); setError(''); setConflict(false);
    try {
      const [next, catalog, event] = await Promise.all([api.profile(), api.interests(), api.event()]);
      if (!active.current) return; setState(next); setInterests(catalog); setSchedule(event);
      const p = next.profile; setValues({ nickname: p?.nickname ?? '', bio: p?.bio ?? '', githubUrl: p?.githubUrl ?? '', blogUrl: p?.blogUrl ?? '', interestIds: (p?.interestIds ?? []).filter(id => catalog.some(item => item.id === id)), accepted: false });
    } catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '프로필을 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  const change = (key: 'nickname' | 'bio' | 'githubUrl' | 'blogUrl', value: string) => { setValues(previous => ({ ...previous, [key]: value })); setError(''); };
  const canWrite = schedule?.status === 'OPEN' || schedule?.status === 'BEFORE_OPEN' && schedule.profilePreparationEnabled;
  async function save() {
    if (busy || !state || !canWrite || conflict) return;
    if (!values.accepted) { setError('참여 안내와 상세 정보 공개 범위를 확인해 주세요.'); form.current?.querySelector<HTMLElement>('[name="policy"]')?.focus(); return; }
    if (!values.bio.trim() || !interestsComplete(values.interestIds, interests)) { setError('한 줄 소개와 관심 산업·개발 분야의 필수 선택을 완료해 주세요.'); return; }
    setBusy(true); setError('');
    try {
      const result = await api.saveProfile({ nickname: values.nickname, bio: values.bio || null, githubUrl: values.githubUrl || null, blogUrl: values.blogUrl || null, expectedVersion: state.profile?.version ?? 0, interestIds: values.interestIds, acceptPolicies: state.consents.policies.filter(p => !p.agreed).map(p => ({ type: p.type, version: p.version })) });
      setState(result); if (runtime) { setNotice('미리보기 프로필을 저장했어요. 실제 프로필과 글자는 바뀌지 않아요.'); return; } auth.acceptUser(await authApi.me()); await participation.refresh(); auth.setFlash(
  '프로필을 저장했어요! 필수 소개와 관심사, 동의가 준비되면 행사 중 첫 번째 S가 자동 지급돼요. 수집판에서 기록을 확인해 주세요.'
  );
      const pending = await participationApi.pending().catch(() => ({ present: false })); navigate(result.participationReady && pending.present ? '/t/resume' : '/me');
    } catch (failure) { setConflict(failure instanceof ApiError && failure.status === 409); setError(failure instanceof Error ? failure.message : '저장하지 못했어요.'); }
    finally { setBusy(false); }
  }
  const identity = state?.identity;
  const showMe = runtime ? { state: runtime.showMeCompleted ? 'COMPLETED' : 'LOCKED' } : participation.collection?.missions.find(mission => mission.code === 'SHOW_ME');
  const rewardChecks = [
    { label: '한 줄 소개', done: !!values.bio.trim(), detail: values.bio.trim() ? '입력됨' : '작성해 주세요' },
    { label: '산업·개발 분야', done: interestsComplete(values.interestIds,interests), detail: interestsComplete(values.interestIds,interests) ? '선택됨' : '각 분류를 골라 주세요' },
    { label: '참여 안내·공개 동의', done: values.accepted, detail: values.accepted ? '확인함' : '아래 안내를 확인해 주세요' },
  ];
  return <div className="page profile-page account-theme"><Link href="/me" className="back-link"><Icon name="arrowLeft" size={16} />내 계정</Link><div className="page-heading"><p className="context-label">SHOW ME · 첫 번째 S</p>{runtime ? <h2>나의 프로필</h2> : <h1>나의 프로필</h1>}<p>닉네임으로 만나고, 상세 소개로 대화를 시작해요.</p></div>
    {notice && <p role="status" className="form-notice">{notice}</p>}{loading ? <p role="status">프로필을 불러오고 있어요.</p> : !state ? <EmptyState title="프로필을 불러오지 못했어요" action={<Button onClick={load}>다시 불러오기</Button>}>{error}</EmptyState> : !state.consents.ready ? <EmptyState title="참여 안내를 준비하고 있어요" action={<Button onClick={load}>다시 확인하기</Button>}>운영자가 참여 안내를 확정한 뒤 프로필을 저장할 수 있어요.</EmptyState> : !identity?.ready ? <EmptyState title="Mattermost 정보를 다시 확인해 주세요" action={<Link href="/auth" className="button button--primary">다시 로그인하기</Link>}>Mattermost 이름을 이름[지역_반] 형식으로 확인하고 다시 로그인해 주세요.</EmptyState> : <><section className="show-me-checklist first-letter-quest" aria-labelledby="show-me-heading"><div className="quest-heading"><span className="quest-letter" aria-hidden="true"><LetterSticker code="SHOW_ME" /><small>목표 글자</small></span><div className="show-me-summary"><h2 id="show-me-heading">{showMe?.state === 'COMPLETED' ? '첫 번째 S를 모았어요' : '첫 번째 S 준비하기'}</h2><p>나를 닮은 소개와 관심사, 첫 S를 만나러 가요.</p></div></div><ul>{rewardChecks.map(check => <li key={check.label} className={check.done ? 'is-ready' : ''}><Icon name={check.done ? 'check' : 'clock'} size={18} /><span>{check.label}<small>{check.detail}</small></span></li>)}</ul><p className="checklist-footnote">프로필을 완성하면 첫 번째 S를 받아요! 입력 후 저장해 주세요.</p></section><div className="profile-grid">
      <form ref={form} className="profile-form" aria-busy={busy} onSubmit={event => { event.preventDefault(); void save(); }}>
        <section aria-labelledby="profile-source"><h2 id="profile-source">{runtime ? '미리보기용 예시 정보' : 'Mattermost에서 가져온 정보'}</h2><div className="profile-source"><ProfileAvatar key={identity.photoUrl} name={identity.realName || ''} src={identity.photoUrl} /><dl><div><dt>이름</dt><dd>{identity.realName}</dd></div><div><dt>지역과 반</dt><dd>{identity.region} · {identity.classNo}반</dd></div></dl></div><p className="profile-section-help">{runtime ? '관리자 미리보기용 예시이며 실제 계정의 정보가 아니에요.' : '이름·반·사진은 Mattermost 프로필을 사용해요. 변경했다면 다시 로그인해 주세요.'}</p></section>
        <section aria-labelledby="profile-basic"><h2 id="profile-basic">동료에게 전할 소개 (필수)</h2><p className="profile-section-help">닉네임과 한 줄 소개를 작성해주세요.</p><Field label="닉네임" name="nickname" value={values.nickname} required maxLength={30} disabled={busy} onChange={event => change('nickname', event.target.value)} /><div className="field profile-bio"><label htmlFor="profile-bio">한 줄 소개 (필수)</label><textarea id="profile-bio" name="bio" required value={values.bio} maxLength={200} rows={3} aria-describedby="profile-bio-help" placeholder="동료에게 나를 소개해 주세요" disabled={busy} onChange={event => change('bio', event.target.value)} /><p id="profile-bio-help" className="field-help">한 줄 소개를 입력해 주세요. <span>{values.bio.length}/200</span></p></div></section>
        <section aria-labelledby="profile-interests"><h2 id="profile-interests">관심사 (필수)</h2><InterestPicker catalog={interests} selected={values.interestIds} disabled={busy} onChange={interestIds => { setError(''); setValues(previous => ({ ...previous,interestIds })); }} /></section>
        <section aria-labelledby="profile-links"><h2 id="profile-links">더 알아볼 수 있는 링크 (선택)</h2><p className="profile-section-help">입력하면 프로필 상세에 보여요. 나를 더 알려보세요.</p><Field label="GitHub 주소 (선택)" name="githubUrl" type="url" value={values.githubUrl} maxLength={2048} disabled={busy} placeholder="https://github.com/…" onChange={event => change('githubUrl', event.target.value)} /><Field label="블로그 주소 (선택)" name="blogUrl" type="url" value={values.blogUrl} maxLength={2048} disabled={busy} placeholder="https://…" onChange={event => change('blogUrl', event.target.value)} /></section>
        <section aria-labelledby="profile-sharing"><h2 id="profile-sharing">참여 안내</h2><p className="profile-disclosure">{disclosure}</p><label className="consent-option"><input type="checkbox" name="policy" checked={values.accepted} disabled={busy} onChange={event => { setError(''); setValues(previous => ({ ...previous, accepted: event.target.checked })); }} /><span>참여 안내와 상세 정보 공개 범위를 확인했어요.</span></label></section>
        {!canWrite && <p className="form-notice" role="status">{schedule?.status === 'NOT_CONFIGURED' ? '행사 일정을 준비하고 있어요.' : '지금은 프로필을 저장할 수 있는 시간이 아니에요.'}</p>}{error && <p className="form-notice is-error" role="alert">{error}</p>}{conflict && <Button variant="secondary" onClick={load}>최신 프로필과 동의 불러오기</Button>}<Button type="submit" className="full-width" disabled={busy || !canWrite || conflict || !values.accepted}>{busy ? '저장 중…' : '프로필 저장하기'}<Icon name="arrow" size={18} /></Button>
      </form>
    </div></>}
  </div>;
}

