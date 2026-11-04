export type AuthMode = 'login' | 'signup' | 'reset' | 'change';
export interface AuthValues { username: string; password: string; confirmation: string; code: string }
export type AuthErrors = Partial<Record<keyof AuthValues, string>>;

export function validateAuth(mode: AuthMode, step: 'account' | 'verify', values: AuthValues): AuthErrors {
  const errors: AuthErrors = {};
  if (mode !== 'change' && !values.username.trim()) errors.username = 'Mattermost 아이디를 입력해 주세요.';
  if (mode === 'login' || step === 'verify') {
    if (!values.password.trim()) errors.password = '비밀번호를 입력해 주세요.';
    else if (mode !== 'login' && (Array.from(values.password).length < 15 || Array.from(values.password).length > 64)) errors.password = '비밀번호는 공백을 포함해 15~64자로 설정해 주세요.';
    if (mode !== 'login' && values.password !== values.confirmation) errors.confirmation = '비밀번호가 일치하지 않아요.';
  }
  if (step === 'verify' && !/^[0-9]{6}$/.test(values.code)) errors.code = '숫자 6자리를 정확히 입력해 주세요.';
  return errors;
}
