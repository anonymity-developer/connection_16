import { Link } from '../app/navigation';
import { Icon } from '../components/Icon';
import { MissionProgress } from '../components/MissionProgress';
import { useAuth } from '../features/auth/context';
import { missions } from '../features/missions/model';
import type { ScreenData } from '../features/missions/model';

/** Read-only reuse of each mission detail's progress and hints, without resolving tags. */
export function MissionOverview({ data }: { data: ScreenData }) {
  const auth = useAuth();
  return <div className="page participant-page mission-overview-page">
    <Link className="back-link" href="/"><Icon name="arrowLeft" size={16} />SSAFY 미션</Link>
    <div className="page-heading"><h1>미션 현황·힌트</h1><p>{data.preview ? '개발용 미리보기 · 실제 기록 없음' : data.serverLoaded ? '나의 진행 현황과 미션별 힌트를 확인해요.' : auth.user ? '참여 기록을 확인하고 있어요. 힌트는 먼저 볼 수 있어요.' : '힌트를 둘러보고, 로그인 후 나의 기록을 확인해요.'}</p></div>
    <div className="mission-overview-list">{missions.map(mission => <section className="mission-overview-item" data-mission={mission.code} key={mission.code}>
      <MissionProgress mission={mission} data={data} signedIn={!!auth.user} />
      <Link className="text-link overview-mission-link" href={`/missions/${mission.slug}`}>{mission.name} 미션 알아보기<Icon name="arrow" size={16} /></Link>
    </section>)}</div>
  </div>;
}
