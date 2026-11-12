import { useRef, useState } from 'react';
import { Button, Dialog } from '../../components/ui';
import { ApiError, request } from '../auth/api';
import type { MemberDetail } from './api';
import '../../styles/support.css';

const actions={UNLOCK:'해금',LOCK:'다시 잠금',GRANT:'획득 부여',REVOKE:'획득 취소·재참여',DEACTIVATE:'계정 비활성화'};
type Action=keyof typeof actions;
type Pending={key:string;url:string;body:{expectedGeneration:number;action:Action;reason:string;confirmation:string}};
export function MemberControls({detail,onChanged,onDenied}:{detail:MemberDetail;onChanged:()=>void;onDenied:(message:string)=>void}) {
  const [selection,setSelection]=useState<{action:Action;code?:string;title:string}|null>(null);
  const [reason,setReason]=useState(''),[confirmation,setConfirmation]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[uncertain,setUncertain]=useState(false),[blocked,setBlocked]=useState(false),[notice,setNotice]=useState('');
  const pending=useRef<Pending|null>(null),sending=useRef(false);
  const inactive=detail.member.status!=='ACTIVE';
  function begin(action:Action,code?:string,title='계정') {setSelection({action,code,title});setReason('');setConfirmation('');setError('');setUncertain(false);setBlocked(false);pending.current=null;}
  async function submit(event:React.FormEvent) {
    event.preventDefault();if(!selection||sending.current||blocked)return;
    const deactivate=selection.action==='DEACTIVATE',word=deactivate?'비활성화':'적용';
    if(!reason.trim()||confirmation!==word){setError(`사유와 확인란의 ‘${word}’를 확인해 주세요.`);return;}
    pending.current??={key:crypto.randomUUID(),url:`/admin/members/${detail.member.id}/${deactivate?'deactivate':`missions/${selection.code}`}`,body:{expectedGeneration:detail.resetGeneration??0,action:selection.action,reason:reason.trim(),confirmation:deactivate?'DEACTIVATE':'APPLY'}};
    sending.current=true;setBusy(true);setError('');
    try {await request(pending.current.url,'POST',pending.current.body,{headers:{'Idempotency-Key':pending.current.key}});pending.current=null;setSelection(null);setNotice('변경했어요. 참가자는 화면을 새로고침해 주세요.');onChanged();}
    catch(failure){const message=failure instanceof Error?failure.message:'변경 결과를 확인하지 못했어요.';if(failure instanceof ApiError&&[401,403].includes(failure.status)){onDenied(message);return;}setError(message);const unknown=!(failure instanceof ApiError)||failure.status>=500;setUncertain(unknown);setBlocked(failure instanceof ApiError&&failure.status===409);if(!unknown)pending.current=null;}
    finally{sending.current=false;setBusy(false);}
  }
  return <section className="admin-panel"><h2>계정·글자 조정</h2>
    <p className="admin-muted">획득 취소와 다시 잠금은 해당 게임의 이전 참여를 보관 후 초기화합니다. 다른 게임은 유지합니다. 획득 부여는 게임 점수·글·성향 결과를 만들지 않습니다.</p>
    {inactive?<p>비활성화된 계정의 이전 기록입니다. 같은 Mattermost 아이디로 다시 회원가입할 수 있어요.</p>:<>
      {detail.missions.map(m=><div className="member-control-row" key={m.code}><strong>{m.title} · {m.state==='COMPLETED'?'획득':m.state==='LOCKED'?'잠김':'해금'}</strong>
        <Button variant="secondary" disabled={busy||m.state!=='LOCKED'||m.code==='SHOW_ME'} onClick={()=>begin('UNLOCK',m.code,m.title)}>해금</Button>
        <Button variant="secondary" disabled={busy||m.state==='LOCKED'} onClick={()=>begin('LOCK',m.code,m.title)}>다시 잠금</Button>
        <Button variant="secondary" disabled={busy||m.state==='COMPLETED'} onClick={()=>begin('GRANT',m.code,m.title)}>획득 부여</Button>
        <Button variant="secondary" disabled={busy||m.state==='LOCKED'&&!m.participated} onClick={()=>begin('REVOKE',m.code,m.title)}>획득 취소·재참여</Button>
      </div>)}
      <p className="admin-muted">첫 S는 NFC가 필요하지 않습니다. 취소 후 프로필을 다시 저장하면 획득 조건을 재확인합니다.</p>
      <Button variant="secondary" disabled={busy} onClick={()=>begin('DEACTIVATE')}>계정 비활성화</Button>
    </>}
    {notice&&<p role="status">{notice}</p>}
    <Dialog open={!!selection} title={selection?`${selection.title} · ${actions[selection.action]}`:'계정·글자 조정'} onClose={()=>{if(!busy)setSelection(null);}}>
      <form className="admin-reset-form" onSubmit={submit} aria-busy={busy} noValidate>
        <p><strong>{detail.member.nickname||detail.member.realName}</strong> · {detail.member.username}</p>
        <p>{selection?.action==='DEACTIVATE'?'현재 세션을 종료하고 계정·글·반응·동료 관계를 서비스에서 제외합니다. 이전 기록은 보관하며 복구하지 않습니다. 다시 이용하려면 회원가입부터 진행해야 합니다.':selection?.action==='GRANT'?'해금과 글자 획득을 부여합니다. 실제 게임 기록은 생성하지 않으며 시상 근거와 구분합니다.':selection?.action==='UNLOCK'?'해당 미션에 태그 없이 접근할 수 있도록 해금합니다. 글자는 획득하지 않습니다.':'해당 게임의 글자 획득·현재 참여와 전체 수집 완료 순서를 취소합니다. 기존 기록은 보관합니다.'}</p>
        <label htmlFor="member-change-reason">변경 사유</label><textarea id="member-change-reason" rows={3} maxLength={300} disabled={busy||uncertain||blocked} value={reason} onChange={e=>setReason(e.target.value)}/>
        <label htmlFor="member-change-confirmation">확인란에 ‘{selection?.action==='DEACTIVATE'?'비활성화':'적용'}’를 입력해 주세요</label><input id="member-change-confirmation" autoComplete="off" value={confirmation} disabled={busy||uncertain||blocked} onChange={e=>setConfirmation(e.target.value)}/>
        {error&&<p role="alert">{error}</p>}{uncertain&&<p>응답을 확인하지 못했어요. 같은 요청으로 다시 확인할 수 있어요.</p>}
        {blocked?<Button onClick={()=>{setSelection(null);onChanged();}}>현재 상태 다시 불러오기</Button>:<Button type="submit" disabled={busy}>{busy?'적용 중…':uncertain?'같은 요청 다시 보내기':'변경 적용'}</Button>}
        <Button variant="quiet" type="button" disabled={busy} onClick={()=>setSelection(null)}>취소</Button>
      </form>
    </Dialog>
  </section>;
}
