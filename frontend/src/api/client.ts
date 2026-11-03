export interface MissionInfo {
  code: string;
  letter: string;
  title: string;
}

export interface ServiceMeta {
  service: string;
  stage: string;
  missions: MissionInfo[];
}

export async function getServiceMeta(signal?: AbortSignal): Promise<ServiceMeta> {
  const response = await fetch('/api/v1/meta', { credentials: 'same-origin', signal });
  if (!response.ok) throw new Error(`API 응답 오류 (${response.status})`);
  // A misconfigured SPA proxy may return index.html with status 200.
  if (!response.headers.get('content-type')?.includes('application/json')) {
    throw new Error('API 프록시 설정을 확인하세요.');
  }
  return response.json() as Promise<ServiceMeta>;
}
