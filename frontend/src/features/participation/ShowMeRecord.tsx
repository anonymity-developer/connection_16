import { useEffect, useState } from 'react';
import { participationApi } from './api';
import type { Interest, ProfileState } from './api';
import { Link } from '../../app/navigation';
import { Button } from '../../components/ui';

export function ShowMeRecord({ preview = false }: { preview?: boolean }) {
  const [profile,setProfile]=useState<ProfileState | null>(null), [catalog,setCatalog]=useState<Interest[]>([]);
  const [error,setError]=useState(''), [retry,setRetry]=useState(0);
  useEffect(()=>{
    let active=true;
    if(!preview)void Promise.all([participationApi.profile(),participationApi.interests()]).then(([state,interests])=>{
      if(active){setProfile(state);setCatalog(interests);setError('');}
    }).catch(failure=>{if(active)setError(failure instanceof Error?failure.message:'프로필을 불러오지 못했어요.');});
    return()=>{active=false;};
  },[preview,retry]);
  return <section className="show-me-record" aria-labelledby="show-me-record-heading"><h2 id="show-me-record-heading">나의 프로필</h2>
    {preview ? <p>개발용 완료 화면 미리보기예요. 실제 프로필은 불러오지 않아요.</p> : error ? <><p role="alert">{error}</p><Button variant="secondary" onClick={()=>setRetry(value=>value+1)}>다시 확인하기</Button></> : !profile ? <p role="status">내 프로필을 불러오고 있어요.</p> : <>
      <p className="show-me-record-name">{profile.profile?.nickname}</p><p className="show-me-record-bio">{profile.profile?.bio}</p>
      <ul className="show-me-record-interests" aria-label="나의 관심사">{catalog.filter(interest=>profile.profile?.interestIds.includes(interest.id)).map(interest=><li key={interest.id}>{interest.name}</li>)}</ul>
      <Link href="/me/profile" className="button button--secondary">프로필 수정하기</Link>
    </>}
  </section>;
}
