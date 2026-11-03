import { useEffect, useState } from 'react';
import { getServiceMeta } from './api/client';
import type { ServiceMeta } from './api/client';

export default function App() {
  const [meta, setMeta] = useState<ServiceMeta | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getServiceMeta(controller.signal).then(setMeta).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '연결 실패');
    });
    return () => controller.abort();
  }, []);

  return (
    <main>
      <p className="eyebrow">SSAFY 16TH · NETWORKING EVENT</p>
      <h1>TAG <span>ME</span></h1>
      <p className="intro">다섯 번의 발견, 새로운 연결.</p>
      <section aria-labelledby="setup-title">
        <h2 id="setup-title">개발 환경 준비 중</h2>
        <p>인증과 미션 기능을 차례로 연결할 시작 화면입니다.</p>
        <div className="status" role="status" aria-live="polite">
          {error ? `백엔드 연결 확인 필요: ${error}` : meta ? '백엔드 API 연결 완료' : '백엔드 연결 확인 중…'}
        </div>
        {meta && <ol className="missions">{meta.missions.map((mission) => (
          <li key={mission.code}><b>{mission.letter}</b><span>{mission.title}</span><small>준비 중</small></li>
        ))}</ol>}
      </section>
      <footer>TAG ME · 함께 발견하는 우리</footer>
    </main>
  );
}
