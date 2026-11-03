import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from '../app/navigation';
import { Button, Dialog, Field } from '../components/ui';
import { Icon } from '../components/Icon';
import { validateAuth } from '../features/auth/validation';
import type { AuthErrors, AuthMode, AuthValues } from '../features/auth/validation';

const copy = {
  login: { eyebrow: 'WELCOME BACK', title: <>반가워요.<br />다시 연결해요.</>, intro: '우리가 만나는 작은 시작, TAG ME.', label: '로그인', submit: '로그인하기' },
  signup: { eyebrow: 'NICE TO MEET YOU', title: <>새로운 연결의<br />첫 페이지.</>, intro: '나를 닮은 동료들을 만나러 가볼까요?', label: '회원가입', submit: '가입하고 시작하기' },
  reset: { eyebrow: 'A FRESH START', title: <>우리의 연결은<br />계속될 거예요.</>, intro: '비밀번호를 잊어도 괜찮아요.', label: '비밀번호 찾기', submit: '새 비밀번호 설정하기' },
  change: { eyebrow: 'KEEP IT YOURS', title: <>소중한 연결,<br />안전하게 지켜요.</>, intro: '본인 확인 후 비밀번호를 바꿔주세요.', label: '비밀번호 변경', submit: '비밀번호 변경하기' },
};

export function Auth({ mode, preview }: { mode: AuthMode; preview: boolean }) {
  const [step, setStep] = useState<'account' | 'verify'>('account');
  const [values, setValues] = useState<AuthValues>({ username: '', password: '', confirmation: '', code: '' });
  const [errors, setErrors] = useState<AuthErrors>({});
  const [notice, setNotice] = useState('');
  const [dialog, setDialog] = useState(false);
  const current = copy[mode];
  const showPassword = mode === 'login' || step === 'verify';
  const set = (name: keyof AuthValues, value: string) => { setValues((previous) => ({ ...previous, [name]: value })); setErrors((previous) => ({ ...previous, [name]: undefined })); setNotice(''); };

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateAuth(mode, step, values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) { setNotice('입력한 내용을 다시 확인해 주세요.'); return; }
    // Phase 1 has no authentication endpoint. Never send or store credentials,
    // mint a fake session, or treat fixture validation as an account login.
    if (!preview) { setNotice('지금은 참여 기능을 준비하고 있어요. 비밀번호와 인증번호는 전송되지 않았어요.'); return; }
    if (mode !== 'login' && step === 'account') { setStep('verify'); setNotice('개발용 화면 미리보기예요. 실제 DM은 발송되지 않았어요.'); return; }
    setDialog(true);
  }

  return <div className="page auth-page"><aside className="auth-story"><div className="event-pill">SSAFY 16TH · TAG ME</div><p className="eyebrow">{current.eyebrow}</p><h1>{current.title}</h1><p>{current.intro}</p><div className="auth-sticker" aria-hidden="true"><span>HELLO</span><strong>새로운<br />우리.</strong><Icon name="sparkles" size={30} /></div><div className="auth-story-bottom"><span>FIVE MISSIONS.</span><span>COUNTLESS CONNECTIONS.</span></div></aside>
    <section className="auth-form-panel" aria-labelledby="auth-heading"><Link className="back-link" href="/">← 홈으로</Link><div className="auth-form-heading"><span className="small-label">LET’S CONNECT</span><h2 id="auth-heading">{current.label}</h2><p>{mode === 'login' ? 'Mattermost 아이디와 TAG ME 비밀번호로 로그인해요.' : step === 'verify' ? 'Mattermost DM으로 받은 6자리 번호를 입력해 주세요.' : mode === 'change' ? '로그인한 본인의 Mattermost DM으로 인증번호를 받아요.' : 'Mattermost DM으로 본인 확인을 진행해요.'}</p></div>
      <form onSubmit={submit} noValidate>
        {mode !== 'change' && <Field label="Mattermost 아이디" name="username" autoComplete="username" placeholder="Mattermost에서 사용하는 아이디" value={values.username} error={errors.username} disabled={step === 'verify'} onChange={(event) => set('username', event.target.value)} />}
        {step === 'verify' && <Field className="otp-input" label="인증번호" name="code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" value={values.code} error={errors.code} hint="숫자 6자리 · 앞자리 0도 그대로 입력해 주세요." onChange={(event) => set('code', event.target.value)} />}
        {showPassword && <Field label={mode === 'login' ? '비밀번호' : '새 비밀번호'} name="password" password autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder={mode === 'login' ? '가입할 때 설정한 TAG ME 비밀번호' : 'TAG ME에서 사용할 비밀번호'} value={values.password} error={errors.password} onChange={(event) => set('password', event.target.value)} />}
        {showPassword && mode !== 'login' && <Field label="새 비밀번호 확인" name="confirmation" password autoComplete="new-password" placeholder="비밀번호를 한 번 더 입력해 주세요" value={values.confirmation} error={errors.confirmation} onChange={(event) => set('confirmation', event.target.value)} />}
        {mode === 'login' && <div className="auth-extra"><span>아이디는 Mattermost와 같아요.</span><Link href="/auth/password-reset">비밀번호를 잊으셨나요?</Link></div>}
        {mode !== 'login' && step === 'account' && <div className="auth-info"><Icon name="message" size={20} /><p>{mode === 'signup' ? '아이디는 그대로, 비밀번호는 새롭게.' : '본인 계정의 DM을 확인할 수 있어야 해요.'}<br /><span>숫자 6자리 인증번호로 본인 확인을 진행해요.</span></p></div>}
        <div className={`form-notice ${Object.keys(errors).length ? 'is-error' : ''}`} role="status" aria-live="polite">{notice}</div>
        <Button type="submit" className="full-width">{mode !== 'login' && step === 'account' ? '인증번호 받기' : current.submit}<Icon name="arrow" size={19} /></Button>
        {step === 'verify' && <Button className="full-width resend-button" variant="quiet" type="button" onClick={() => setNotice('개발용 미리보기에서는 DM을 발송하지 않아요.')}>인증번호 다시 받기</Button>}
      </form>
      {mode === 'login' ? <p className="auth-switch">아직 TAG ME가 처음이라면? <Link href="/auth/signup">회원가입</Link></p> : <p className="auth-switch">이미 계정이 있나요? <Link href="/auth">로그인으로 돌아가기</Link></p>}
      <p className="auth-footnote"><Icon name="lock" size={13} />Mattermost 비밀번호를 입력하는 곳이 아니에요.</p>
    </section><Dialog open={dialog} title="입력 화면을 확인했어요" onClose={() => setDialog(false)}><p>개발용 UI 미리보기에서 입력 형식만 확인했어요. 실제 가입, 로그인, 비밀번호 변경이나 DM 인증은 실행되지 않았어요.</p><Button className="full-width" onClick={() => setDialog(false)}>확인했어요</Button></Dialog>
  </div>;
}
