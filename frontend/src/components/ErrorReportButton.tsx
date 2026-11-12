import { useRef, useState } from 'react';
import { useAuth } from '../features/auth/context';
import { ApiError, request } from '../features/auth/api';
import { Link } from '../app/navigation';
import { Button, Dialog } from './ui';
import '../styles/support.css';

export function ErrorReportButton({ path }: { path: string }) {
  const auth=useAuth();
  const [open,setOpen]=useState(false),[body,setBody]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[done,setDone]=useState(false),[uncertain,setUncertain]=useState(false);
  const pending=useRef<{key:string;body:{body:string;pagePath:string}}|null>(null),sending=useRef(false);
  async function send(event:React.FormEvent) {
    event.preventDefault();if(sending.current||!body.trim())return;
    const pagePath=path.split(/[?#]/)[0];
    pending.current??={key:crypto.randomUUID(),body:{body:body.trim(),pagePath:pagePath.startsWith('/t/')?'/t/[tag]':pagePath}};
    sending.current=true;setBusy(true);setError('');
    try {await request('/error-reports','POST',pending.current.body,{headers:{'Idempotency-Key':pending.current.key}});setDone(true);setUncertain(false);pending.current=null;setBody('');}
    catch(failure){setError(failure instanceof Error?failure.message:'신고를 보내지 못했어요.');const unknown=!(failure instanceof ApiError)||failure.status>=500;setUncertain(unknown);if(!unknown)pending.current=null;}
    finally{sending.current=false;setBusy(false);}
  }
  return <><button type="button" className="header-report" onClick={()=>{setOpen(true);setDone(false);setError('');}}>오류 신고</button>
    <Dialog open={open} title="오류 신고" onClose={()=>{if(!busy)setOpen(false);}}>
      {!auth.user?<><p>로그인하면 오류 내용을 보낼 수 있어요.</p><Link href="/auth" className="button button--primary" onClick={()=>setOpen(false)}>로그인하기</Link></>:done?<><p role="status">오류 신고를 보냈어요.</p><Button onClick={()=>setOpen(false)}>확인</Button></>:<form className="support-form" onSubmit={send} aria-busy={busy}>
        <p>신고에는 현재 계정과 페이지 정보가 자동으로 포함돼요.</p>
        <label htmlFor="error-report-body">오류 내용</label><textarea id="error-report-body" rows={5} maxLength={400} value={body} disabled={busy||uncertain} onChange={event=>setBody(Array.from(event.target.value).slice(0,200).join(''))} required aria-describedby="error-report-count"/>
        <span id="error-report-count" className="support-count">{Array.from(body).length} / 200자</span>
        {error&&<p role="alert">{error}</p>}{uncertain&&<p>전송 결과를 확인하지 못했어요. 같은 신고로 다시 시도할 수 있어요.</p>}
        <Button type="submit" disabled={busy||!body.trim()}>{busy?'보내는 중…':uncertain?'같은 신고 다시 보내기':'신고 보내기'}</Button>
      </form>}
    </Dialog></>;
}
