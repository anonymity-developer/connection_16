import { useEffect, useRef, useState } from 'react';
import { Link } from '../../app/navigation';
import { Button } from '../../components/ui';
import { ApiError } from '../auth/api';
import type { tapRankingApi, TapBoard } from './api';

export function TapLeaderboard({ api, revision, preview = false }: { api: typeof tapRankingApi; revision: string; preview?: boolean }) {
  const [board,setBoard]=useState<TapBoard | null>(null), [text,setText]=useState(''), [error,setError]=useState('');
  const [loading,setLoading]=useState(true), [busy,setBusy]=useState(false), [uncertain,setUncertain]=useState(false), [blocked,setBlocked]=useState(false);
  const version=useRef(0), submitting=useRef(false), pending=useRef<{attemptId:number;content:string;key:string} | null>(null);
  useEffect(()=>{
    const current=++version.current;
    setLoading(true);setBoard(null);setError('');setText('');setUncertain(false);setBlocked(false);setBusy(false);submitting.current=false;pending.current=null;
    void api.board().then(next=>{if(current===version.current)setBoard(next);}).catch(failure=>{if(current===version.current)setError(failure instanceof Error?failure.message:'순위를 불러오지 못했어요.');}).finally(()=>{if(current===version.current)setLoading(false);});
    return()=>{version.current++;};
  },[api,revision]);
  async function reload() {
    const current=++version.current;setLoading(true);setError('');
    try{const next=await api.board();if(current===version.current){setBoard(next);setBlocked(false);setUncertain(false);pending.current=null;setText('');}}
    catch(failure){if(current===version.current){setError(failure instanceof Error?failure.message:'순위를 불러오지 못했어요.');if(failure instanceof ApiError && [401,403].includes(failure.status))setBoard(null);}}
    finally{if(current===version.current)setLoading(false);}
  }
  async function save() {
    if(submitting.current || busy || blocked || !board?.own?.canWriteMessage || !text.trim())return;
    const current=version.current;submitting.current=true;setBusy(true);setError('');
    pending.current??={attemptId:board.own.attemptId,content:text.trim(),key:crypto.randomUUID()};
    try{const attempt=pending.current,next=await api.message(attempt.attemptId,attempt.content,attempt.key);if(current===version.current){setBoard(next);pending.current=null;setText('');setUncertain(false);}}
    catch(failure){if(current===version.current){setError(failure instanceof Error?failure.message:'소감 응답을 확인하지 못했어요.');const unknown=!(failure instanceof ApiError)||failure.status>=500;setUncertain(unknown);setBlocked(failure instanceof ApiError&&failure.status===409);if(!unknown)pending.current=null;if(failure instanceof ApiError&&[401,403].includes(failure.status))setBoard(null);}}
    finally{if(current===version.current){submitting.current=false;setBusy(false);}}
  }
  return <section className="tap-leaderboard" aria-labelledby="tap-leaderboard-heading"><div className="tap-ranking-heading"><h2 id="tap-leaderboard-heading">상위 10명</h2><Button variant="quiet" disabled={loading||busy} onClick={reload}>순위 새로고침</Button></div>
    {preview && <p className="result-footnote">관리자 미리보기 순위예요. 실제 기록에는 반영되지 않아요.</p>}
    {loading ? <p role="status">순위를 확인하고 있어요.</p> : board && <>
      {board.own && <p className="tap-own-rank">내 순위 <strong>{board.own.rank===null?'순위 없음':`${board.own.rank}위`}</strong></p>}
      {board.top.length ? <ol className="tap-ranking-list">{board.top.map(entry=><li key={entry.rank}><span className="tap-rank-number">{entry.rank}위</span><div>{entry.profileId && !preview ? <Link className="tap-rank-profile" href={`/discovery/profiles/${entry.profileId}`}>{entry.nickname}</Link> : <strong>{entry.nickname}</strong>}{entry.message && <p>{entry.message}</p>}</div><span className="tap-rank-score">{entry.tapCount}회</span></li>)}</ol> : <p>아직 공개된 공식 기록이 없어요.</p>}
      {board.own?.message && (board.own.rank===null || board.own.rank>10) && <p className="tap-own-message"><b>내 소감</b> {board.own.message}</p>}
      {board.own?.canWriteMessage && <form onSubmit={event=>{event.preventDefault();void save();}} aria-busy={busy}>
        <label htmlFor="tap-record-message">순위권 한마디</label><p className="result-footnote">상위 10위에 들었어요. 50자 이내로 한 번만 남길 수 있어요.</p>
        <textarea id="tap-record-message" rows={3} maxLength={50} value={text} disabled={busy||uncertain||blocked} onChange={event=>setText(event.target.value)} />
        <p className="result-footnote">{text.length}/50</p><Button type="submit" disabled={busy||blocked||!text.trim()}>{busy?'저장 중…':uncertain?'같은 소감 다시 보내기':'소감 남기기'}</Button>
      </form>}
    </>}
    {error && <p role="alert" className="form-notice is-error">{error}</p>}
  </section>;
}
