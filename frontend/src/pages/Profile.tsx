import { useEffect, useRef, useState } from 'react';
import { Link, navigate } from '../app/navigation';
import { Button, Dialog, EmptyState, Field } from '../components/ui';
import { Icon } from '../components/Icon';
import { ProfileAvatar } from '../components/ProfileAvatar';
import { authApi, ApiError } from '../features/auth/api';
import { useAuth } from '../features/auth/context';
import { participationApi } from '../features/participation/api';
import type { EventState, Interest, Policy, ProfileState } from '../features/participation/api';
import { useParticipation } from '../features/participation/context';

const disclosure = '목록에는 닉네임이 표시되며, 닉네임을 누르면 이름·지역·반·사진과 작성한 소개·관심사·링크가 참여자에게 공개돼요.';
export function ProfilePage() {
  const auth = useAuth(), participation = useParticipation();
  const [state, setState] = useState<ProfileState | null>(null), [interests, setInterests] = useState<Interest[]>([]), [schedule, setSchedule] = useState<EventState | null>(null);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState(''), [conflict, setConflict] = useState(false), [policy, setPolicy] = useState<Policy | null>(null);
  const [values, setValues] = useState({ nickname: '', bio: '', githubUrl: '', blogUrl: '', interestIds: [] as number[], accepted: false });
  const form = useRef<HTMLFormElement>(null), active = useRef(true);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, []);
  async function load() {
    setLoading(true); setError(''); setConflict(false);
    try {
      const [next, catalog, event] = await Promise.all([participationApi.profile(), participationApi.interests(), participationApi.event()]);
      if (!active.current) return; setState(next); setInterests(catalog); setSchedule(event);
      const p = next.profile; setValues({ nickname: p?.nickname ?? '', bio: p?.bio ?? '', githubUrl: p?.githubUrl ?? '', blogUrl: p?.blogUrl ?? '', interestIds: p?.interestIds ?? [], accepted: next.consents.policies.length > 0 && next.consents.policies.every(p => p.agreed) });
    } catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '프로필을 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  const change = (key: 'nickname' | 'bio' | 'githubUrl' | 'blogUrl', value: string) => { setValues(previous => ({ ...previous, [key]: value })); setError(''); };
  const canWrite = schedule?.status === 'OPEN' || schedule?.status === 'BEFORE_OPEN' && schedule.profilePreparationEnabled;
  async function save() {
    if (busy || !state || !canWrite || conflict) return;
    if (!values.accepted) { setError('참여 안내와 상세 정보 공개 범위를 확인해 주세요.'); form.current?.querySelector<HTMLElement>('[name="policy"]')?.focus(); return; }
    setBusy(true); setError('');
    try {
      const result = await participationApi.saveProfile({ nickname: values.nickname, bio: values.bio || null, githubUrl: values.githubUrl || null, blogUrl: values.blogUrl || null, expectedVersion: state.profile?.version ?? 0, interestIds: values.interestIds, acceptPolicies: state.consents.policies.filter(p => !p.agreed).map(p => ({ type: p.type, version: p.version })) });
      setState(result); auth.acceptUser(await authApi.me()); await participation.refresh(); auth.setFlash('프로필을 저장했어요. SHOW ME 태그를 열면 글자를 받을 수 있어요.');
      const pending = await participationApi.pending().catch(() => ({ present: false })); navigate(result.participationReady && pending.present ? '/t/resume' : '/me');
    } catch (failure) { setConflict(failure instanceof ApiError && failure.status === 409); setError(failure instanceof Error ? failure.message : '저장하지 못했어요.'); }
    finally { setBusy(false); }
  }
  const identity = state?.identity;
  return <div className="page profile-page"><Link href="/me" className="back-link"><Icon name="arrowLeft" size={16} />내 계정</Link><div className="page-heading"><p className="context-label">SHOW ME · 첫 번째 S</p><h1>나의 프로필</h1><p>닉네임으로 만나고, 상세 소개로 대화를 시작해요.</p></div>
    {loading ? <p role="status">프로필을 불러오고 있어요.</p> : !state ? <EmptyState title="프로필을 불러오지 못했어요" action={<Button onClick={load}>다시 불러오기</Button>}>{error}</EmptyState> : !state.consents.ready ? <EmptyState title="참여 안내를 준비하고 있어요" action={<Button onClick={load}>다시 확인하기</Button>}>운영자가 참여 안내를 확정한 뒤 프로필을 저장할 수 있어요.</EmptyState> : !identity?.ready ? <EmptyState title="Mattermost 정보를 다시 확인해 주세요" action={<Link href="/auth" className="button button--primary">다시 로그인하기</Link>}>Mattermost 이름을 이름[지역_반] 형식으로 확인하고 다시 로그인해 주세요.</EmptyState> : <div className="profile-grid">
      <form ref={form} className="profile-form" aria-busy={busy} onSubmit={event => { event.preventDefault(); void save(); }}>
        <section aria-labelledby="profile-source"><h2 id="profile-source">Mattermost에서 가져온 정보</h2><div className="profile-source"><ProfileAvatar key={identity.photoUrl} name={identity.realName} src={identity.photoUrl} /><dl><div><dt>이름</dt><dd>{identity.realName}</dd></div><div><dt>지역과 반</dt><dd>{identity.region} · {identity.classNo}반</dd></div></dl></div><p className="profile-section-help">이름·반·사진은 Mattermost 프로필을 사용해요. 변경했다면 다시 로그인해 주세요.</p></section>
        <section aria-labelledby="profile-basic"><h2 id="profile-basic">동료에게 전할 소개</h2><p className="profile-section-help">닉네임은 필수예요. 나머지는 편하게 채워 주세요.</p><Field label="닉네임" name="nickname" value={values.nickname} required maxLength={30} disabled={busy} onChange={event => change('nickname', event.target.value)} /><div className="field profile-bio"><label htmlFor="profile-bio">한 줄 소개 (선택)</label><textarea id="profile-bio" name="bio" value={values.bio} maxLength={200} rows={3} disabled={busy} onChange={event => change('bio', event.target.value)} /><p className="field-help">{values.bio.length}/200</p></div></section>
        <section aria-labelledby="profile-interests"><div className="profile-interest-heading"><h2 id="profile-interests">관심사 (선택)</h2><span>{values.interestIds.length}/10개 선택</span></div><p className="profile-section-help">함께 이야기하고 싶은 분야와 기술을 골라 주세요.</p>{[['DOMAIN', '관심 도메인'], ['TECH_STACK', '기술 스택']].map(([category, title]) => <fieldset key={category} className="interest-options" disabled={busy}><legend>{title}</legend><div>{interests.filter(i => (i.category || 'DOMAIN') === category).map(interest => <label key={interest.id}><input type="checkbox" name="interest" checked={values.interestIds.includes(interest.id)} disabled={!values.interestIds.includes(interest.id) && values.interestIds.length >= 10} onChange={event => { setError(''); setValues(previous => ({ ...previous, interestIds: event.target.checked ? [...previous.interestIds, interest.id] : previous.interestIds.filter(id => id !== interest.id) })); }} /><span>{interest.name}</span></label>)}</div></fieldset>)}</section>
        <section aria-labelledby="profile-links"><h2 id="profile-links">더 알아볼 수 있는 링크</h2><p className="profile-section-help">입력한 링크는 프로필 상세에서 보여요.</p><Field label="GitHub 주소 (선택)" name="githubUrl" type="url" value={values.githubUrl} maxLength={2048} disabled={busy} placeholder="https://github.com/…" onChange={event => change('githubUrl', event.target.value)} /><Field label="블로그 주소 (선택)" name="blogUrl" type="url" value={values.blogUrl} maxLength={2048} disabled={busy} placeholder="https://…" onChange={event => change('blogUrl', event.target.value)} /></section>
        <section aria-labelledby="profile-sharing"><h2 id="profile-sharing">참여 안내</h2><p className="profile-disclosure">{disclosure}</p><label className="consent-option"><input type="checkbox" name="policy" checked={values.accepted} disabled={busy || state.consents.policies.every(p => p.agreed)} onChange={event => { setError(''); setValues(previous => ({ ...previous, accepted: event.target.checked })); }} /><span>참여 안내와 상세 정보 공개 범위를 확인했어요.</span></label><div className="profile-policy-links">{state.consents.policies.map(p => <button key={p.type} type="button" className="text-button" onClick={() => setPolicy(p)}>{p.title} 내용 보기</button>)}</div></section>
        {!canWrite && <p className="form-notice" role="status">{schedule?.status === 'NOT_CONFIGURED' ? '행사 일정을 준비하고 있어요.' : '지금은 프로필을 저장할 수 있는 시간이 아니에요.'}</p>}{error && <p className="form-notice is-error" role="alert">{error}</p>}{conflict && <Button variant="secondary" onClick={load}>최신 프로필과 동의 불러오기</Button>}<Button type="submit" className="full-width" disabled={busy || !canWrite || conflict}>{busy ? '저장 중…' : '프로필 저장하기'}<Icon name="arrow" size={18} /></Button>
      </form>
      <aside className="profile-preview" aria-labelledby="profile-preview-heading"><h2 id="profile-preview-heading">동료에게 보일 첫인사</h2><p className="profile-section-help">목록에서는 닉네임으로 만나요.</p><div className="profile-initial" aria-hidden="true">{Array.from(values.nickname.trim())[0] || 'T'}</div><p className="profile-preview-name">{values.nickname || '닉네임을 정해 주세요'}</p>{values.bio && <p>{values.bio}</p>}<div className="profile-interest-preview">{interests.filter(i => values.interestIds.includes(i.id)).map(i => <span key={i.id}>{i.name}</span>)}</div><p className="profile-preview-scope">닉네임을 누르면 사진과 전체 소개가 공개돼요.</p><div className="profile-reward-note"><b>SHOW ME · 첫 번째 S</b><p>프로필을 저장한 뒤 현장 태그를 열면 글자를 받아요. 태그를 먼저 열었다면 저장할 때 지급돼요.</p></div></aside>
    </div>}
    <Dialog open={!!policy} title="참여 안내 확인" onClose={() => setPolicy(null)}><p className="policy-title">{policy?.title}</p><p className="policy-version">버전 {policy?.version}</p><p className="policy-body">{policy?.body}</p><Button onClick={() => setPolicy(null)}>확인했어요</Button></Dialog>
  </div>;
}
