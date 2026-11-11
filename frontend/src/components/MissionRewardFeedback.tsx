import { useEffect, useState } from 'react';
import { useAuth } from '../features/auth/context';
import { useParticipation } from '../features/participation/context';
import { missions } from '../features/missions/model';
import type { MissionCode } from '../features/missions/model';
import { Link } from '../app/navigation';
import { LetterSticker } from './LetterSticker';
import { Button, Dialog } from './ui';

/** Presentation only: awards are read from the authenticated server collection. */
export function MissionRewardFeedback({ path }: { path: string }) {
  const auth = useAuth(), participation = useParticipation();
  const [earned, setEarned] = useState<MissionCode[]>([]);
  const completed = participation.collection?.missions.filter(m => m.state === 'COMPLETED').map(m => m.code);
  useEffect(() => {
    if (path === '/' || !auth.user || auth.user.admin || !participation.collection || !participation.celebrations) return;
    const completed = participation.collection.missions.filter(m => m.state === 'COMPLETED').map(m => m.code);
    const next = participation.celebrations.takeLetters(completed);
    if (next.length) setEarned(previous => [...new Set([...previous, ...next])]);
  }, [path, auth.user, participation.collection, participation.celebrations]);
  const visible = auth.user && !auth.user.admin ? earned.filter(code => completed?.includes(code)) : [];
  const rewards = missions.filter(m => visible.includes(m.code));
  const reward = rewards[0];
  const letter = reward?.code === 'SHOW_ME' ? '첫 번째 S' : reward?.code === 'SYNC_ME' ? '두 번째 S' : reward?.letter;
  const title = rewards.length === 1 ? `${letter}를 획득했어요` : '새로운 글자를 획득했어요';
  const close = () => setEarned([]);
  return <Dialog open={rewards.length > 0} title={title} onClose={close}>
    <div className="mission-earned-artwork">{rewards.map(m => <LetterSticker key={m.code} code={m.code} collectionState="collected" celebrate />)}</div>
    <p className="mission-earned-names">{rewards.map(m => m.name).join(' · ')}</p>
    <div className="mission-earned-actions"><Button onClick={close}>계속 참여하기</Button><Link href="/" className="button button--secondary" onClick={close}>수집판 보기</Link></div>
  </Dialog>;
}
