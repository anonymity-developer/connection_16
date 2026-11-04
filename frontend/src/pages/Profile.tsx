import { useEffect, useRef, useState } from 'react';
import { Link, navigate } from '../app/navigation';
import { Button, Dialog, EmptyState, Field } from '../components/ui';
import { Icon } from '../components/Icon';
import { authApi, ApiError } from '../features/auth/api';
import { useAuth } from '../features/auth/context';
import { participationApi } from '../features/participation/api';
import type { EventState, Interest, Policy, ProfileState } from '../features/participation/api';
import { useParticipation } from '../features/participation/context';

export function ProfilePage() {
  const auth = useAuth(); const participation = useParticipation();
  const [state, setState] = useState<ProfileState | null>(null); const [interests, setInterests] = useState<Interest[]>([]);
  const [schedule, setSchedule] = useState<EventState | null>(null);
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [conflict, setConflict] = useState(false); const [policy, setPolicy] = useState<Policy | null>(null);
  const [values, setValues] = useState({ nickname: '', realName: '', classNo: '', bio: '', githubUrl: '', blogUrl: '', isPublic: true, interestIds: [] as number[], accepted: [] as string[] });
  const form = useRef<HTMLFormElement>(null); const active = useRef(true);
  useEffect(() => { active.current = true; void load(); return () => { active.current = false; }; }, []);
  async function load() {
    setLoading(true); setError(''); setConflict(false);
    try {
      const [next, catalog, event] = await Promise.all([participationApi.profile(), participationApi.interests(), participationApi.event()]);
      if (!active.current) return;
      setState(next); setInterests(catalog); setSchedule(event);
      const p = next.profile;
      setValues({ nickname: p?.nickname ?? '', realName: p?.realName ?? '', classNo: p?.classNo?.toString() ?? '', bio: p?.bio ?? '', githubUrl: p?.githubUrl ?? '', blogUrl: p?.blogUrl ?? '', isPublic: p?.isPublic ?? true, interestIds: p?.interestIds ?? [], accepted: next.consents.policies.filter(p => p.agreed).map(p => p.type) });
    } catch (failure) { if (active.current) setError(failure instanceof Error ? failure.message : '프로필을 불러오지 못했어요.'); }
    finally { if (active.current) setLoading(false); }
  }
  const change = (key: 'nickname' | 'realName' | 'classNo' | 'bio' | 'githubUrl' | 'blogUrl', value: string) => { setValues(previous => ({ ...previous, [key]: value })); setError(''); };
  const canWrite = schedule?.status === 'OPEN' || schedule?.status === 'BEFORE_OPEN' && schedule.profilePreparationEnabled;
  async function save() {
    if (busy || !state || !canWrite) return;
    if (!values.interestIds.length) { setError('관심사를 하나 이상 선택해 주세요.'); form.current?.querySelector<HTMLElement>('[name="interest"]')?.focus(); return; }
    if (state.consents.policies.some(p => (p.type === 'PARTICIPATION' || values.isPublic) && !values.accepted.includes(p.type))) { setError('공개 범위와 필수 동의 문구를 확인해 주세요.'); form.current?.querySelector<HTMLElement>('[name="policy"]:not(:checked)')?.focus(); return; }
    setBusy(true); setError('');
    try {
      const result = await participationApi.saveProfile({ nickname: values.nickname, realName: values.realName, classNo: Number(values.classNo), bio: values.bio, githubUrl: values.githubUrl || null, blogUrl: values.blogUrl || null, isPublic: values.isPublic, expectedVersion: state.profile?.version ?? 0, interestIds: values.interestIds, acceptPolicies: state.consents.policies.filter(p => !p.agreed && values.accepted.includes(p.type)).map(p => ({ type: p.type, version: p.version })) });
      setState(result); auth.acceptUser(await authApi.me()); await participation.refresh();
      auth.setFlash(result.participationReady ? '프로필을 저장했어요. SHOW ME 태그를 열면 글자를 받을 수 있어요.' : '프로필을 비공개로 저장했어요.');
      const pending = await participationApi.pending().catch(() => ({ present: false }));
      navigate(result.participationReady && pending.present ? '/t/resume' : '/me');
    } catch (failure) { setConflict(failure instanceof ApiError && failure.status === 409); setError(failure instanceof Error ? failure.message : '저장하지 못했어요.'); }
    finally { setBusy(false); }
  }
  async function withdraw() {
    if (!state || busy) return;
    setBusy(true); setError('');
    try {
      await participationApi.withdraw('PROFILE_PUBLICATION', state.consents.policies.find(p => p.type === 'PROFILE_PUBLICATION')?.version ?? 'current');
      auth.acceptUser(await authApi.me()); await load(); await participation.refresh(); auth.setFlash('공개 동의를 철회하고 프로필을 비공개로 전환했어요. 기존 글자 기록은 유지돼요.');
    } catch (failure) { setError(failure instanceof Error ? failure.message : '공개 설정을 바꾸지 못했어요.'); }
    finally { setBusy(false); }
  }
  return <div className="page profile-page"><Link href="/me" className="back-link"><Icon name="arrowLeft" size={16} />내 계정</Link>
    <div className="page-heading"><p className="context-label">SHOW ME · 첫 번째 S</p><h1>나의 프로필</h1><p>동료에게 보일 소개와 공개 범위를 정해요.</p></div>
    {loading ? <p role="status">프로필을 불러오고 있어요.</p> : !state ? <EmptyState title="프로필을 불러오지 못했어요" action={<Button onClick={load}>다시 불러오기</Button>}>{error}</EmptyState> : !state.consents.ready ? <EmptyState title="참여 안내를 준비하고 있어요" action={<Button onClick={load}>다시 확인하기</Button>}>운영자가 동의 문구를 확정한 뒤 프로필을 저장할 수 있어요.</EmptyState> : <div className="profile-grid">
      <form ref={form} className="profile-form" aria-busy={busy} onSubmit={event => { event.preventDefault(); void save(); }}>
        <section aria-labelledby="profile-basic"><h2 id="profile-basic">동료에게 전할 소개</h2><p className="profile-section-help">목록에는 닉네임이, 공개 프로필 상세에는 이름과 반도 표시돼요.</p>
          <Field label="닉네임" name="nickname" value={values.nickname} required maxLength={30} disabled={busy} onChange={event => change('nickname', event.target.value)} />
          <div className="profile-pair"><Field label="이름" name="realName" value={values.realName} required maxLength={100} disabled={busy} onChange={event => change('realName', event.target.value)} /><Field label="서울캠퍼스 반" name="classNo" type="number" min={1} max={9999} value={values.classNo} required disabled={busy} onChange={event => change('classNo', event.target.value)} /></div>
          <p className="field-help">이름과 반은 직접 입력한 정보로 공개돼요.</p>
          <div className="field profile-bio"><label htmlFor="profile-bio">한 줄 소개</label><textarea id="profile-bio" name="bio" value={values.bio} required maxLength={200} rows={3} disabled={busy} onChange={event => change('bio', event.target.value)} /><p className="field-help">{values.bio.length}/200</p></div>
          <fieldset className="interest-options" disabled={busy}><legend>관심사 <span>1~5개 선택</span></legend><div>{interests.map(interest => <label key={interest.id}><input type="checkbox" name="interest" checked={values.interestIds.includes(interest.id)} disabled={!values.interestIds.includes(interest.id) && values.interestIds.length >= 5} onChange={event => { setError(''); setValues(previous => ({ ...previous, interestIds: event.target.checked ? [...previous.interestIds, interest.id] : previous.interestIds.filter(id => id !== interest.id) })); }} /><span>{interest.name}</span></label>)}</div></fieldset>
        </section>
        <section aria-labelledby="profile-links"><h2 id="profile-links">더 알아볼 수 있는 링크</h2><p className="profile-section-help">링크는 선택이에요. 공개 프로필 상세에서 보여요.</p><Field label="GitHub 주소 (선택)" name="githubUrl" type="url" value={values.githubUrl} maxLength={2048} disabled={busy} placeholder="https://github.com/…" onChange={event => change('githubUrl', event.target.value)} /><Field label="블로그 주소 (선택)" name="blogUrl" type="url" value={values.blogUrl} maxLength={2048} disabled={busy} placeholder="https://…" onChange={event => change('blogUrl', event.target.value)} /></section>
        <section aria-labelledby="profile-sharing"><h2 id="profile-sharing">공개 범위와 동의</h2><p className="profile-section-help">공개에 동의한 소개는 로그인한 참여자가 볼 수 있어요. 공개 동의는 언제든 철회할 수 있어요.</p>
          <label className="consent-option"><input type="checkbox" checked={values.isPublic} disabled={busy} onChange={event => { setError(''); setValues(previous => ({ ...previous, isPublic: event.target.checked })); }} /><span>내 프로필을 참여자에게 공개하기<small>비공개 상태에서는 SHOW ME 글자가 새로 지급되지 않아요.</small></span></label>
          {state.consents.policies.map(p => <div className="policy-row" key={`${p.type}:${p.version}`}><label className="consent-option"><input type="checkbox" name="policy" checked={values.accepted.includes(p.type)} disabled={busy || p.agreed} onChange={event => { setError(''); setValues(previous => ({ ...previous, accepted: event.target.checked ? [...previous.accepted, p.type] : previous.accepted.filter(type => type !== p.type) })); }} /><span>{p.title}<small>{p.type === 'PARTICIPATION' || values.isPublic ? '필수' : '공개 시 필수'} · {p.version}{p.agreed ? ' · 동의 완료' : ''}</small></span></label><button type="button" className="text-button" onClick={() => setPolicy(p)}>내용 보기</button></div>)}
        </section>
        {!canWrite && <p className="form-notice" role="status">{schedule?.status === 'NOT_CONFIGURED' ? '행사 일정을 준비하고 있어요.' : '지금은 프로필을 저장할 수 있는 시간이 아니에요.'} 공개 동의 철회는 아래에서 할 수 있어요.</p>}
        {error && <p className="form-notice is-error" role="alert">{error}</p>}{conflict && <Button variant="secondary" onClick={load}>최신 프로필과 동의 불러오기</Button>}
        <Button type="submit" className="full-width" disabled={busy || !canWrite || conflict}>{busy ? '저장 중…' : '프로필 저장하기'}<Icon name="arrow" size={18} /></Button>
        {state.profile?.isPublic && <Button variant="quiet" className="full-width" disabled={busy} onClick={withdraw}>공개 동의 철회하고 비공개로 전환</Button>}
      </form>
      <aside className="profile-preview" aria-labelledby="profile-preview-heading"><h2 id="profile-preview-heading">동료에게 보일 첫인사</h2><p className="profile-section-help">입력 중인 내용의 미리보기예요.</p><div className="profile-initial" aria-hidden="true">{values.nickname.trim().slice(0,1) || 'S'}</div><p className="profile-preview-name">{values.nickname || '닉네임을 정해 주세요'}</p><p>{values.bio || '나를 소개할 한마디를 적어 주세요.'}</p><div className="profile-interest-preview">{interests.filter(i => values.interestIds.includes(i.id)).map(i => <span key={i.id}>{i.name}</span>)}</div><p className="profile-preview-scope"><Icon name="lock" size={16} />{values.isPublic ? '이름·반·링크는 상세에서 공개돼요.' : '비공개로 저장돼요.'}</p><div className="profile-reward-note"><b>SHOW ME · 첫 번째 S</b><p>프로필과 동의를 저장한 뒤 현장 태그를 열면 글자를 받아요. 태그를 먼저 열었다면 저장할 때 지급돼요.</p></div></aside>
    </div>}
    <Dialog open={!!policy} title="동의 내용 확인" onClose={() => setPolicy(null)}><p className="policy-title">{policy?.title}</p><p className="policy-version">버전 {policy?.version}</p><p className="policy-body">{policy?.body}</p><Button onClick={() => setPolicy(null)}>확인했어요</Button></Dialog>
  </div>;
}
