import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from '../app/navigation';
import { Button, Dialog, Field } from '../components/ui';
import { Icon } from '../components/Icon';
import { validateAuth } from '../features/auth/validation';
import type { AuthErrors, AuthMode, AuthValues } from '../features/auth/validation';

const copy = {
  login: { title: '로그인', description: 'Mattermost 아이디와 TAG ME 비밀번호로 시작해요.', submit: '로그인하기' },
  signup: { title: '회원가입', description: 'Mattermost 아이디를 그대로 사용하고, 비밀번호는 새로 설정해요.', submit: '가입하기' },
  reset: { title: '비밀번호 재설정', description: 'Mattermost DM으로 본인을 확인한 뒤 새 비밀번호를 설정해요.', submit: '비밀번호 재설정하기' },
  change: { title: '비밀번호 변경', description: '로그인한 본인의 Mattermost DM으로 인증번호를 받아 변경해요.', submit: '비밀번호 변경하기' },
};

export function Auth({ mode, preview }: { mode: AuthMode; preview: boolean }) {
  const [step, setStep] = useState<'account' | 'verify'>('account');
  const [values, setValues] = useState<AuthValues>({ username: '', password: '', confirmation: '', code: '' });
  const [errors, setErrors] = useState<AuthErrors>({});
  const [notice, setNotice] = useState('');
  const [dialog, setDialog] = useState(false);
  const form = useRef<HTMLFormElement>(null);
  const focusInvalid = useRef(false);
  const current = copy[mode];
  const showPassword = mode === 'login' || step === 'verify';
  const passwordLabel = mode === 'login' || mode === 'signup' ? '비밀번호' : '새 비밀번호';
  const set = (name: keyof AuthValues, value: string) => { setValues((previous) => ({ ...previous, [name]: value })); setErrors((previous) => ({ ...previous, [name]: undefined })); setNotice(''); };

  useEffect(() => {
    if (focusInvalid.current) { form.current?.querySelector<HTMLInputElement>('[aria-invalid="true"]')?.focus(); focusInvalid.current = false; }
  }, [errors]);
  useEffect(() => {
    if (step === 'verify') form.current?.querySelector<HTMLInputElement>('[name="code"]')?.focus();
  }, [step]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateAuth(mode, step, values);
    focusInvalid.current = Object.keys(nextErrors).length > 0;
    setErrors(nextErrors);
    if (focusInvalid.current) { setNotice('입력한 내용을 다시 확인해 주세요.'); return; }
    // UI only: never transmit credentials, store them, or create a fake session.
    if (!preview) { setNotice('참여 기능 준비 중이에요. 비밀번호와 인증번호는 전송되지 않았어요.'); return; }
    if (mode !== 'login' && step === 'account') { setStep('verify'); setNotice('개발용 미리보기예요. 실제 DM은 발송되지 않았어요.'); return; }
    setDialog(true);
  }
  function changeAccount() {
    setValues((previous) => ({ ...previous, password: '', confirmation: '', code: '' }));
    setErrors({}); setNotice(''); setStep('account');
    requestAnimationFrame(() => form.current?.querySelector<HTMLInputElement>('[name="username"]')?.focus());
  }

  return <div className="page auth-page"><Link className="back-link" href={mode === 'change' ? '/me' : '/'}><Icon name="arrowLeft" size={16} />{mode === 'change' ? '마이페이지' : '미션 모으기'}</Link>
    <div className="auth-grid"><section className="auth-form-panel" aria-labelledby="auth-heading"><div className="auth-form-heading"><p className="context-label">TAG ME 계정</p><h1 id="auth-heading">{current.title}</h1><p>{current.description}</p></div>
      <div className="form-boundary"><Icon name="clock" size={17} /><p>화면 미리보기예요. 실제 인증과 DM 발송은 아직 연결되지 않았어요.</p></div>
      {mode !== 'login' && <ol className="auth-steps" aria-label="본인 확인 단계"><li aria-current={step === 'account' ? 'step' : undefined}>계정 확인</li><li aria-current={step === 'verify' ? 'step' : undefined}>6자리 인증 · 비밀번호 설정</li></ol>}
      <form ref={form} onSubmit={submit} noValidate>
        {mode !== 'change' && <Field label="Mattermost 아이디" name="username" autoComplete="username" autoCapitalize="none" spellCheck={false} placeholder="Mattermost에서 사용하는 아이디" value={values.username} error={errors.username} disabled={step === 'verify'} onChange={(event) => set('username', event.target.value)} />}
        {step === 'verify' && <><div className="verify-heading"><p>DM으로 받은 번호를 입력해 주세요.</p>{mode !== 'change' && <button type="button" className="text-button" onClick={changeAccount}>아이디 변경</button>}</div><Field className="otp-input" label="인증번호" name="code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" value={values.code} error={errors.code} hint="숫자 6자리 · 앞자리 0도 그대로 입력해 주세요." onChange={(event) => set('code', event.target.value)} /></>}
        {showPassword && <Field label={passwordLabel} name="password" password autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder={mode === 'login' ? 'TAG ME 가입 시 설정한 비밀번호' : 'TAG ME에서 사용할 비밀번호'} value={values.password} error={errors.password} onChange={(event) => set('password', event.target.value)} />}
        {showPassword && mode !== 'login' && <Field label={`${passwordLabel} 확인`} name="confirmation" password autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력해 주세요" value={values.confirmation} error={errors.confirmation} onChange={(event) => set('confirmation', event.target.value)} />}
        {mode === 'login' && <div className="auth-extra"><Link href="/auth/password-reset">비밀번호를 잊으셨나요?</Link></div>}
        <div className={`form-notice ${Object.keys(errors).some((key) => errors[key as keyof AuthErrors]) ? 'is-error' : ''}`} role="status" aria-live="polite">{notice}</div>
        <Button type="submit" className="full-width">{mode !== 'login' && step === 'account' ? '인증번호 받기' : current.submit}<Icon name="arrow" size={18} /></Button>
        {step === 'verify' && <Button className="full-width resend-button" variant="quiet" type="button" onClick={() => setNotice('개발용 미리보기에서는 DM을 발송하지 않아요.')}>인증번호 다시 받기</Button>}
      </form>
      {mode === 'login' ? <p className="auth-switch">처음 오셨나요? <Link href="/auth/signup">회원가입</Link></p> : mode === 'change' ? <p className="auth-switch"><Link href="/me">변경을 취소하고 돌아가기</Link></p> : <p className="auth-switch"><Link href="/auth">로그인으로 돌아가기</Link></p>}
    </section><aside className="auth-guide" aria-labelledby="account-guide"><Icon name="tag" size={28} /><h2 id="account-guide">아이디는 그대로.<br />비밀번호는 TAG ME에서.</h2><p>동료들과 새로운 연결을 시작하기 전에, 본인 계정을 확인해요.</p><dl><div><dt>아이디</dt><dd>Mattermost에서 사용하는 아이디</dd></div><div><dt>비밀번호</dt><dd>TAG ME에 처음 가입할 때 설정한 값</dd></div><div><dt>6자리 인증</dt><dd>가입 · 비밀번호 변경 · 분실 재설정 시 Mattermost DM으로 확인</dd></div></dl><p className="auth-footnote"><Icon name="lock" size={16} />Mattermost 비밀번호를 입력하지 마세요.</p></aside></div>
    <Dialog open={dialog} title="입력 형식을 확인했어요" onClose={() => setDialog(false)}><p>개발용 미리보기예요. 실제 가입, 로그인, 비밀번호 변경이나 DM 인증은 실행되지 않았어요.</p><Button className="full-width" onClick={() => setDialog(false)}>확인했어요</Button></Dialog>
  </div>;
}
