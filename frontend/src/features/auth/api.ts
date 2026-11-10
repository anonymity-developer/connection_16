export interface SessionUser { id: number; username: string; nickname: string | null; onboardingRequired: boolean; admin?: boolean }
export interface AuthConfig { available: boolean; passwordMinLength: number; passwordMaxLength: number; participantAvailable?: boolean; adminAvailable?: boolean }
export interface OtpChallenge { challengeId: string; expiresIn: number; resendAfter: number }
export type OtpPurpose = 'SIGN_UP' | 'PASSWORD_CHANGE' | 'PASSWORD_RESET';
export class ApiError extends Error {
  status: number;
  code: string;
  retryAfter: number;
  constructor(status: number, code: string, message: string, retryAfter = 0) {
    super(message); this.status = status; this.code = code; this.retryAfter = retryAfter;
  }
}
let csrf: { headerName: string; token: string } | null = null;
export function clearCsrf() { csrf = null; }

export async function request<T>(path: string, method = 'GET', body?: object, options: { headers?: Record<string, string>; keepalive?: boolean } = {}): Promise<T> {
  const headers: Record<string, string> = { ...options.headers };
  if (method !== 'GET') {
    if (!csrf) csrf = await request<{ headerName: string; token: string }>('/csrf');
    headers[csrf.headerName] = csrf.token;
    headers['Content-Type'] = 'application/json';
  }
  let response: Response;
  try { response = await fetch(`/api/v1${path}`, { method, credentials: 'same-origin', cache: 'no-store', keepalive: options.keepalive, headers, body: body === undefined ? undefined : JSON.stringify(body) }); }
  catch { throw new ApiError(503, 'NETWORK_ERROR', '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
  if (response.status === 204 || response.status === 202 && path === '/tags/pending') return undefined as T;
  let data;
  try { data = await response.json(); }
  catch { throw new ApiError(503, 'AUTH_UNAVAILABLE', '서버 응답을 확인할 수 없어요. 잠시 후 다시 시도해 주세요.'); }
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) clearCsrf();
    if (response.status === 401 && path !== '/users/me' && ['UNAUTHENTICATED', 'SESSION_EXPIRED'].includes(data.code)) window.dispatchEvent(new Event('tagme:unauthenticated'));
    throw new ApiError(response.status, data.code ?? 'REQUEST_FAILED', data.message ?? '요청을 처리하지 못했어요.', Number(response.headers.get('Retry-After') ?? data.retryAfter ?? 0));
  }
  return data as T;
}
export const authApi = {
  config: () => request<AuthConfig>('/auth/config'),
  me: () => request<SessionUser>('/users/me'),
  async login(username: string, password: string) { const user = await request<SessionUser>('/auth/login', 'POST', { username, password }); clearCsrf(); return user; },
  async signup(challengeId: string, code: string, password: string) { const user = await request<SessionUser>('/auth/signup', 'POST', { challengeId, code, password }); clearCsrf(); return user; },
  otp: (purpose: OtpPurpose, username?: string) => request<OtpChallenge>('/auth/otp/request', 'POST', { purpose, ...(username === undefined ? {} : { username }) }),
  async password(mode: 'reset' | 'change', challengeId: string, code: string, newPassword: string) {
    await request<void>(mode === 'change' ? '/users/me/password' : '/auth/password/reset', mode === 'change' ? 'PUT' : 'POST', { challengeId, code, newPassword }); clearCsrf();
  },
  async logout() { await request<void>('/auth/logout', 'POST'); clearCsrf(); },
};
