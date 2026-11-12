import { useEffect, useRef, useState } from 'react';
import '../styles/account.css';
import { ConnectionStickers } from '../components/EventArtwork';
import type { FormEvent } from 'react';
import { Link, navigate } from '../app/navigation';
import { Button, Dialog, Field } from '../components/ui';
import { Icon } from '../components/Icon';
import { ApiError, authApi } from '../features/auth/api';
import type { OtpChallenge, OtpPurpose } from '../features/auth/api';
import { useAuth } from '../features/auth/context';
import { validateAuth } from '../features/auth/validation';
import type { AuthErrors, AuthMode, AuthValues } from '../features/auth/validation';

const copy = {
  login: { title: '로그인', description: '반가워요! 내 계정으로 다섯 미션을 이어가요.', submit: '로그인하기' },
  signup: { title: '회원가입', description: '우리의 연결을 시작해요. DM 인증 후 TAG ME 비밀번호를 만들어요.', submit: '가입하기' },
  reset: { title: '비밀번호 재설정', description: 'Mattermost DM으로 본인을 확인한 뒤 새 비밀번호를 설정해요.', submit: '비밀번호 재설정하기' },
  change: { title: '비밀번호 변경', description: '로그인한 본인의 Mattermost DM으로 인증번호를 받아 변경해요.', submit: '비밀번호 변경하기' },
};

export function Auth({ mode, preview }: { mode: AuthMode; preview: boolean }) {
  const auth = useAuth();
  const { setFlash } = auth;
  const [busy, setBusy] = useState(false);
  const [challenge, setChallenge] = useState<OtpChallenge | null>(null);
  const [expiresAt, setExpiresAt] = useState(0);
  const [resendAt, setResendAt] = useState(0);
  const [now, setNow] = useState(Date.now());
  const resendRemaining = Math.max(0, Math.ceil((resendAt - now) / 1000));
  const expiresRemaining = Math.max(0, Math.ceil((expiresAt - now) / 1000));
  const purpose: OtpPurpose = mode === 'change' ? 'PASSWORD_CHANGE' : mode === 'reset' ? 'PASSWORD_RESET' : 'SIGN_UP';
  const [step, setStep] = useState<'account' | 'verify'>('account');
  const [values, setValues] = useState<AuthValues>({ username: '', password: '', confirmation: '', code: '' });
  const [errors, setErrors] = useState<AuthErrors>({});
  const [notice, setNotice] = useState('');
  const [failed, setFailed] = useState(false);
  const [dialog, setDialog] = useState(false);
  const form = useRef<HTMLFormElement>(null);
  const focusInvalid = useRef(false);
  const current = copy[mode];
  const showPassword = mode === 'login' || step === 'verify';
  const passwordLabel = mode === 'login' || mode === 'signup' ? '비밀번호' : '새 비밀번호';
  const set = (name: keyof AuthValues, value: string) => { setValues((previous) => ({ ...previous, [name]: value })); setErrors((previous) => ({ ...previous, [name]: undefined })); setNotice(''); setFailed(false); auth.setFlash(''); };

  useEffect(() => {
    if (mode === 'change') setFlash('');
  }, [mode, setFlash]);

  useEffect(() => {
    if (focusInvalid.current) { form.current?.querySelector<HTMLInputElement>('[aria-invalid="true"]')?.focus(); focusInvalid.current = false; }
  }, [errors]);
  useEffect(() => {
    if (step === 'verify') form.current?.querySelector<HTMLInputElement>('[name="code"]')?.focus();
  }, [step]);

  useEffect(() => {
    if (!challenge && !resendAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [challenge, resendAt]);

  async function requestCode() {
    if (busy) return;
    if (preview) { setStep('verify'); setNotice('개발용 미리보기예요. 실제 DM은 발송되지 않았어요.'); return; }
    if (!(auth.config.participantAvailable ?? auth.config.available)) { setNotice('참가자 인증 서비스를 준비하고 있어요. 입력값은 전송되지 않았어요.'); return; }
    setBusy(true); setNotice(''); setFailed(false);
    try {
      const next = await authApi.otp(purpose, mode === 'change' ? undefined : values.username.trim());
      const received = Date.now();
      setChallenge(next); setExpiresAt(received + next.expiresIn * 1000); setResendAt(received + next.resendAfter * 1000); setNow(received);
      setValues((previous) => ({ ...previous, code: '' })); setStep('verify');
      setNotice('발송 가능한 계정이면 Mattermost DM으로 번호가 도착해요. DM을 확인해 주세요.');
    } catch (failure) { showFailure(failure); }
    finally { setBusy(false); }
  }
  function showFailure(failure: unknown) {
    setFailed(true);
    if (failure instanceof ApiError && failure.retryAfter > 0) { setResendAt(Date.now() + failure.retryAfter * 1000); setNow(Date.now()); }
    if (failure instanceof ApiError && failure.code === 'AUTH_RESTART_REQUIRED') { setChallenge(null); setStep('account'); setValues((previous) => ({ ...previous, code: '', password: '', confirmation: '' })); }
    setNotice(failure instanceof Error ? failure.message : '요청을 처리하지 못했어요.');
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const nextErrors = validateAuth(mode, step, values, auth.config);
    focusInvalid.current = Object.keys(nextErrors).length > 0;
    setErrors(nextErrors); setFailed(focusInvalid.current);
    if (focusInvalid.current) { setNotice('입력한 내용을 다시 확인해 주세요.'); return; }
    if (mode !== 'login' && step === 'account') { await requestCode(); return; }
    if (preview) { setDialog(true); return; }
    if (!auth.config.available) { setNotice('인증 서비스를 준비하고 있어요. 비밀번호와 인증번호는 전송되지 않았어요.'); return; }
    if (mode !== 'login' && (!challenge || Date.now() >= expiresAt)) { setFailed(true); setNotice('인증 시간이 지났어요. 인증번호를 새로 받아주세요.'); return; }
    setBusy(true); setNotice(''); setFailed(false); auth.setFlash('');
    try {
      if (mode === 'login' || mode === 'signup') {
        const user = mode === 'login' ? await authApi.login(values.username.trim(), values.password)
          : await authApi.signup(challenge!.challengeId, values.code, values.password);
        auth.acceptUser(user); setValues({ username: '', password: '', confirmation: '', code: '' });
        navigate(user.admin ? '/admin' : user.onboardingRequired ? mode === 'signup' ? '/me/profile' : '/me' : auth.takeReturnPath());
      } else {
        await authApi.password(mode, challenge!.challengeId, values.code, values.password);
        auth.acceptUser(null); setValues({ username: '', password: '', confirmation: '', code: '' });
        auth.setFlash('비밀번호를 변경했어요. 모든 기존 세션이 종료됐으니 새 비밀번호로 로그인해 주세요.'); navigate('/auth');
      }
    } catch (failure) { showFailure(failure); }
    finally { setBusy(false); }
  }
  function changeAccount() {
    setValues((previous) => ({ ...previous, password: '', confirmation: '', code: '' }));
    setErrors({}); setNotice(''); setFailed(false); setStep('account'); setChallenge(null); setExpiresAt(0); setResendAt(0);
    requestAnimationFrame(() => form.current?.querySelector<HTMLInputElement>('[name="username"]')?.focus());
  }

  return <div className="page auth-page account-theme participant-page"><Link className="back-link" href={mode === 'change' ? '/me' : '/'}><Icon name="arrowLeft" size={16} />{mode === 'change' ? '마이페이지' : '미션 모으기'}</Link>
    <div className="auth-grid"><section className="auth-form-panel" aria-labelledby="auth-heading"><div className="auth-form-heading"><p className="context-label">SSAFY 16기 · TAG ME</p><h1 id="auth-heading">{current.title}</h1><p>{current.description}</p></div>
      <div className="form-boundary"><Icon name={auth.config.available && !preview ? 'lock' : 'clock'} size={17} /><p>{preview ? '개발용 화면 미리보기예요. 실제 인증과 DM 발송은 실행되지 않아요.' : !auth.ready ? '인증 서비스 연결을 확인하고 있어요.' : auth.config.available ? '아이디는 Mattermost와 같아요. 비밀번호는 TAG ME 전용이에요.' : '인증 서비스를 준비하고 있어요. 지금은 화면을 둘러볼 수 있어요.'}</p></div>
      {mode !== 'login' && <ol className="auth-steps" aria-label="본인 확인 단계"><li aria-current={step === 'account' ? 'step' : undefined}>계정 입력<span>Mattermost 아이디</span></li><li aria-current={step === 'verify' ? 'step' : undefined}>DM 인증 · 비밀번호<span>6자리 번호와 새 비밀번호</span></li></ol>}
      <form ref={form} onSubmit={submit} noValidate aria-busy={busy}>
        {mode !== 'change' && <Field label={mode === 'login' ? '아이디' : 'Mattermost 아이디'} name="username" maxLength={64} autoComplete="username" autoCapitalize="none" spellCheck={false} placeholder={mode === 'login' ? 'Mattermost 또는 관리자 아이디' : 'Mattermost에서 사용하는 아이디'} value={values.username} error={errors.username} disabled={busy || step === 'verify'} onChange={(event) => set('username', event.target.value)} />}
        {step === 'verify' && <><div className="verify-heading"><p>DM으로 받은 번호를 입력해 주세요.</p>{mode !== 'change' && <button type="button" className="text-button" disabled={busy} onClick={changeAccount}>아이디 변경</button>}</div><Field className="otp-input" label="인증번호" name="code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" value={values.code} error={errors.code} disabled={busy} hint={challenge ? expiresRemaining > 0 ? `남은 시간 ${Math.floor(expiresRemaining / 60)}:${String(expiresRemaining % 60).padStart(2, '0')} · 앞자리 0도 그대로 입력해 주세요.` : '인증 시간이 지났어요. 번호를 새로 받아주세요.' : '숫자 6자리 · 앞자리 0도 그대로 입력해 주세요.'} onChange={(event) => set('code', event.target.value)} /></>}
        {step === 'verify' && <div className="auth-password-heading"><h2>TAG ME 비밀번호 설정</h2><p>인증번호와 함께 확인해요.</p></div>}
        {showPassword && <Field label={passwordLabel} name="password" password disabled={busy} maxLength={128} hint={mode === 'login' ? undefined : `공백을 포함해 ${auth.config.passwordMinLength}~${auth.config.passwordMaxLength}자. 입력한 값을 그대로 사용해요.`} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder={mode === 'login' ? 'TAG ME 가입 시 설정한 비밀번호' : 'TAG ME에서 사용할 비밀번호'} value={values.password} error={errors.password} onChange={(event) => set('password', event.target.value)} />}
        {showPassword && mode !== 'login' && <Field label={`${passwordLabel} 확인`} name="confirmation" password disabled={busy} maxLength={128} autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력해 주세요" value={values.confirmation} error={errors.confirmation} onChange={(event) => set('confirmation', event.target.value)} />}
        {mode === 'login' && <div className="auth-extra"><Link href="/auth/password-reset">비밀번호를 잊으셨나요?</Link></div>}
        <div className={`form-notice ${failed ? 'is-error' : ''}`} role={failed ? 'alert' : 'status'}>{notice || (mode === 'change' ? '' : auth.flash)}</div>
        <Button type="submit" className="full-width" disabled={busy || !auth.ready || !preview && mode !== 'login' && step === 'account' && resendRemaining > 0}>{busy ? '처리 중…' : mode !== 'login' && step === 'account' ? '인증번호 받기' : current.submit}<Icon name="arrow" size={18} /></Button>
        {step === 'verify' && <Button className="full-width resend-button" variant="quiet" type="button" disabled={busy || resendRemaining > 0} onClick={() => preview ? setNotice('개발용 미리보기에서는 DM을 발송하지 않아요.') : requestCode()}>{resendRemaining > 0 ? `${resendRemaining}초 후 다시 받기` : '인증번호 다시 받기'}</Button>}
      </form>
      {mode === 'login' ? <p className="auth-switch account-card-footer">처음 오셨나요? <Link href="/auth/signup">회원가입</Link></p> : mode === 'change' ? <p className="auth-switch account-card-footer"><Link href="/me">변경을 취소하고 돌아가기</Link></p> : <p className="auth-switch account-card-footer"><Link href="/auth">로그인으로 돌아가기</Link></p>}
    </section><aside className="auth-guide" aria-labelledby="account-guide"><ConnectionStickers className="auth-stickers" /><h2 id="account-guide">NFC 태그로 미션을 찾아보세요</h2><p>로그인하고 프로필을 등록하면 태그한 미션을 이어서 할 수 있어요.</p><dl><div><dt>아이디</dt><dd>Mattermost에서 사용하는 아이디</dd></div><div><dt>비밀번호</dt><dd>TAG ME에 처음 가입할 때 설정한 값</dd></div><div><dt>6자리 인증</dt><dd>가입과 비밀번호 변경·재설정에만 필요해요.</dd></div></dl><p className="auth-footnote"><Icon name="lock" size={16} />Mattermost 비밀번호를 입력하지 마세요.</p></aside></div>
    <Dialog open={dialog} title="입력 형식을 확인했어요" onClose={() => setDialog(false)}><p>개발용 미리보기예요. 실제 가입, 로그인, 비밀번호 변경이나 DM 인증은 실행되지 않았어요.</p><Button className="full-width" onClick={() => setDialog(false)}>확인했어요</Button></Dialog>
  </div>;
}
