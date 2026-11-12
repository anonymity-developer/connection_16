import type { AuthConfig } from './api';

export const passwordPolicy = { passwordMinLength: 8, passwordMaxLength: 20 };

export type AuthMode = 'login' | 'signup' | 'reset' | 'change';
export interface AuthValues { username: string; password: string; confirmation: string; code: string }
export type AuthErrors = Partial<Record<keyof AuthValues, string>>;

export function validateAuth(mode: AuthMode, step: 'account' | 'verify', values: AuthValues, { passwordMinLength, passwordMaxLength }: Pick<AuthConfig, 'passwordMinLength' | 'passwordMaxLength'> = passwordPolicy): AuthErrors {
  const errors: AuthErrors = {};
  if (mode !== 'change' && !values.username.trim()) errors.username = 'Mattermost 아이디를 입력해 주세요.';
  if (mode === 'login' || step === 'verify') {
    if (!values.password.trim()) errors.password = '비밀번호를 입력해 주세요.';
    else if (mode !== 'login' && (Array.from(values.password).length < passwordMinLength || Array.from(values.password).length > passwordMaxLength)) errors.password = `비밀번호는 공백을 포함해 ${passwordMinLength}~${passwordMaxLength}자로 설정해 주세요.`;
    if (mode !== 'login' && values.password !== values.confirmation) errors.confirmation = '비밀번호가 일치하지 않아요.';
  }
  if (step === 'verify' && !/^[0-9]{6}$/.test(values.code)) errors.code = '숫자 6자리를 정확히 입력해 주세요.';
  return errors;
}
