// Offline development fixture; never registered as the live questionnaire.
import draft from './sync-questionnaire.proposal.json';
import type { resultsApi, SyncResult, TapAttempt } from '../features/results/api';
import type { contentApi, ContentKind, OwnSlot, PublicContent, Revision } from '../features/content/api';
import type { reactionsApi, OwnComment, PublicComment } from '../features/content/reactions-api';
import type { MissionCode, MissionState } from '../features/missions/model';
import { createCollectionCelebrations } from '../features/participation/collection-celebrations';
const clone = <T,>(value: T): T => structuredClone(value);
const empty = (): OwnSlot => ({ id:null,version:0,visibility:null,approvedRevision:null,pendingRevision:null,latestRevision:null,missionCompleted:false,luckScore:null,landingZone:null });
export const demoPeers = [
  {id:91,nickname:'가상 동료 · 민트',interests:['Python','산책'],bio:'함께 배워가며 작은 아이디어를 나누고 싶어요.',reason:'공통 관심사 · Python'},
  {id:92,nickname:'가상 동료 · 하늘',interests:['디자인','음악'],bio:'좋아하는 음악과 디자인 이야기를 나눠요.',reason:'같은 검토용 설문 버전의 성향'},
  {id:93,nickname:'가상 동료 · 라일락',interests:['Python','독서'],bio:'책 한 권과 새로운 대화를 좋아해요.',reason:'공통 관심사 · Python'},
];
const wishes = [
  '마음 맞는 동료들과 멋진 프로젝트를 완성하고 싶어요.', '내 속도를 믿고 꾸준히 성장하기를!',
  '엘리베이터에서 먼저 반갑게 인사할 수 있기를.', '함께 웃을 일이 많아지는 한 주가 되길.',
  '새로운 도전 앞에서 조금 더 용감해지고 싶어요.', '서로의 꿈을 응원하는 동료를 만나고 싶어요.',
  '건강하게 오늘의 공부를 끝낼 수 있기를.', '좋아하는 일을 오래 즐기고 싶어요.',
  '이번 주에는 막혔던 기능을 끝까지 만들어 보고 싶어요.', '쉬는 시간에 새로운 동료에게 먼저 말을 걸고 싶어요.',
  '혼자 풀기 어려운 문제도 같이 풀어보고 싶어요.', '처음부터 완벽해야 한다는 마음을 내려놓고 싶어요.',
  '이번 프로젝트에서 누군가에게 도움이 되는 기능을 만들고 싶어요.', '금요일에 팀원들과 맛있는 점심을 먹고 싶어요.',
  '새로 배운 내용을 동료에게 설명해 보고 싶어요.', '내가 좋아하는 기술이 뭔지 조금 더 알아가고 싶어요.',
  '포기하지 않고 만든 첫 결과물을 보여주고 싶어요.', '작은 성취도 함께 기뻐할 수 있으면 좋겠어요.',
  '이번 주에는 충분히 자고 건강하게 지내고 싶어요.', '같이 공부할 동료에게 고맙다고 말하고 싶어요.',
  '어려운 일이 생겨도 편하게 도움을 청할 수 있기를.', '오늘의 작은 도전이 다음 기회로 이어졌으면 좋겠어요.',
];
const cheers = ['오늘의 작은 한 걸음도 충분히 멋져요.','함께 배우는 우리 모두를 응원해요!','막히는 날도 성장하는 중이에요.'];
const publicItem = (content:string,id:number,anonymous=true):PublicContent => ({id,content,anonymous,nickname:anonymous?null:'가상 동료',profileId:null,templateId:'NOTE',style:'ROSE',luckScore:null,landingZone:null});
/** Development memory only. No fetch, storage, credentials, tag token or DB adapter. */
export function createExperience(wait:()=>Promise<void> = async()=>{}) {
  if (!import.meta.env.DEV) throw new Error('Development experience is unavailable');
  let revision=0; const listeners=new Set<()=>void>();
  const progress:Record<MissionCode,MissionState>={SHOW_ME:'LOCKED',SYNC_ME:'LOCKED',MAKE_A_WISH:'LOCKED',FAST_TAP:'LOCKED',SAY_SOMETHING:'LOCKED'};
  const celebrations = createCollectionCelebrations();
  celebrations.observe([]);
  const slots:Record<ContentKind,OwnSlot>={wishes:empty(),cheers:empty()};
  const feeds:Record<ContentKind,PublicContent[]>={wishes:wishes.map((x,i)=>publicItem(x,100+i)),cheers:cheers.map((x,i)=>publicItem(x,200+i,i!==1))};
  const ownComments=new Map<number,OwnComment[]>();const publicComments=new Map<number,PublicComment[]>();const likes=new Map<number,boolean>();const requests=new Map<string,OwnSlot>();
  let sync:SyncResult|null=null;let tap:TapAttempt|null=null;let failNext=false;let counter=1000;let wishLanding:'statue'|'water'|null=null;let profile={nickname:'',bio:'',interests:[] as string[]};
  const notify=()=>{revision++;listeners.forEach(fn=>fn());};
  const change=(code:MissionCode,state:MissionState)=>{progress[code]=state;notify();};
  const unlockTag = (code: Exclude<MissionCode, 'SHOW_ME'>) => { if (progress[code] === 'LOCKED') change(code, 'UNLOCKED'); };
  const requireTag = (code: MissionCode) => { if (progress[code] === 'LOCKED') throw new Error('먼저 가상 NFC 태그로 미션을 열어 주세요.'); };
  const ready=async()=>{await wait();if(failNext){failNext=false;throw new Error('개발용 오류예요. 실제 요청은 전송하지 않았어요.');}};
  const heart=(id:number)=>({liked:likes.get(id)||false,count:2+(likes.get(id)?1:0),canLike:true});
  const content:typeof contentApi={
    own:async kind=>{await ready();return clone(slots[kind]);},
    feed:async(kind,cursor)=>{await ready();const all=feeds[kind].filter(x=>!cursor||x.id<Number(cursor)).sort((a,b)=>b.id-a.id);return {items:clone(all.slice(0,20)),nextCursor:all.length>20?all[19].id:null};},
    submit:async(kind,editing,key,input)=>{await ready();const old=requests.get(kind+key);if(old)return clone(old);const code=kind==='wishes'?'MAKE_A_WISH':'SAY_SOMETHING';requireTag(code);const slot=slots[kind];if(editing&&input.expectedVersion!==slot.version)throw new Error('가상 수정본 버전이 달라요. 새로 확인해 주세요.');if(!input.content.trim() || input.content.length>400)throw new Error('내용을 400자 이내로 입력해 주세요.');
      const next:Revision={...input,id:++counter,itemId:slot.id||counter,number:(slot.latestRevision?.number||0)+1,submittedAt:new Date().toISOString(),status:'QUEUED',reason:null};
      Object.assign(slot,{id:slot.id||next.itemId,version:slot.version+1,visibility:'VISIBLE',latestRevision:next,pendingRevision:next,missionCompleted:true});requests.set(kind+key,clone(slot));change(code,'COMPLETED');return clone(slot);},
  };
  const reactions:typeof reactionsApi={
    hearts:async ids=>Object.fromEntries(ids.map(id=>[id,heart(id)])),heart:async id=>heart(id),
    setHeart:async(id,desired)=>{await ready();likes.set(id,desired);notify();return heart(id);},
    comments:async id=>({items:clone(publicComments.get(id)||[]),nextCursor:null}),own:async id=>({items:clone(ownComments.get(id)||[]),nextCursor:null}),
    submit:async(id,key,content,revealProfile)=>{await ready();const rows=ownComments.get(id)||[];const old=rows.find(x=>x.requestKey===key);if(old)return clone(old);const row:OwnComment={id:++counter,content,revealProfile,status:'QUEUED',reason:null,submittedAt:new Date().toISOString(),requestKey:key};ownComments.set(id,[row,...rows]);notify();return clone(row);},
  };
  const results:typeof resultsApi={
    sync:async()=>({result:clone(sync)}),questions:async()=>({available:true,version:'DEV-DRAFT',title:draft.title,questions:draft.questions.map(q=>({id:q.id,text:q.text,options:q.options.map(o=>({id:o.id,label:o.label}))}))}),
    submitSync:async(version,answers)=>{await ready();if(sync)return clone(sync);requireTag('SYNC_ME');if(version!=='DEV-DRAFT'||answers.length!==draft.questions.length||new Set(answers.map(x=>x.questionId)).size!==draft.questions.length)throw new Error('가상 질문에 모두 답해 주세요.');const scores:Record<string,number>=Object.fromEntries(draft.axes.map(axis=>[axis.id,0]));for(const q of draft.questions){const o=q.options.find(o=>o.id===answers.find(a=>a.questionId===q.id)?.optionId);if(!o)throw new Error('선택하지 않은 질문이 있어요.');for(const [axis,n] of Object.entries(o.scores))scores[axis]+=n;}
      const code=draft.axes.map(a=>scores[a.id]>0?a.positive:a.negative).join('');const type=draft.types.find(t=>t.code===code)!;sync={questionnaireVersion:version,type:{...type,title:'가상 결과 · '+type.title},axisScores:scores,submittedAt:new Date().toISOString()};change('SYNC_ME','COMPLETED');return clone(sync);},
    tap:async()=>{if(tap&&['COUNTDOWN','PLAYING','AWAITING_SUBMISSION'].includes(tap.status)){const now=Date.now();tap.status=now<Date.parse(tap.startsAt)?'COUNTDOWN':now<Date.parse(tap.endsAt)?'PLAYING':'AWAITING_SUBMISSION';}return {officialAvailable:!tap||tap.status==='CANCELLED',attempt:clone(tap)};},
    start:async()=>{await ready();requireTag('FAST_TAP');if(tap&&tap.status!=='CANCELLED')return clone(tap);const now=Date.now();tap={attemptId:++counter,generation:1,status:'COUNTDOWN',serverNow:new Date(now).toISOString(),startsAt:new Date(now+5000).toISOString(),endsAt:new Date(now+15000).toISOString(),submitDeadline:new Date(now+27000).toISOString(),tapCount:null,reviewReason:null,provisional:true};notify();return clone(tap);},
    cancel:async id=>{if(!tap||tap.attemptId!==id)throw new Error('가상 기록 없음');tap.status=Date.now()<Date.parse(tap.startsAt)?'CANCELLED':'INTERRUPTED';notify();return clone(tap);},
    finish:async payload=>{await ready();if(!tap||tap.attemptId!==payload.attemptId)throw new Error('가상 기록 없음');if(tap.status==='VALID')return clone(tap);if(Date.now()<Date.parse(tap.endsAt))throw new Error('가상 게임 시간이 아직 끝나지 않았어요.');if(payload.taps.some(t=>t<0||t>=10000))throw new Error('10초 범위의 가상 입력만 확인해요.');tap={...tap,status:'VALID',tapCount:payload.taps.length};change('FAST_TAP','COMPLETED');return clone(tap);},
  };
  function moderate(kind:ContentKind,status:'APPROVED'|'REJECTED') {
    for(const [id,rows]of ownComments){ownComments.set(id,rows.map(x=>({...x,status})));publicComments.set(id,status==='APPROVED'?rows.map(x=>({id:x.id,content:x.content,revealProfile:x.revealProfile,nickname:x.revealProfile?'가상 작성자':null,profileId:null})):[]);}
    const slot=slots[kind];if(!slot.latestRevision){notify();return;}slot.latestRevision={...slot.latestRevision,status};slot.pendingRevision=null;slot.version++;
    if(status==='APPROVED'){slot.approvedRevision=clone(slot.latestRevision);slot.missionCompleted=true;feeds[kind]=[publicItem(slot.approvedRevision.content,slot.id!,slot.approvedRevision.anonymous),...feeds[kind].filter(x=>x.id!==slot.id)];progress[kind==='wishes'?'MAKE_A_WISH':'SAY_SOMETHING']='COMPLETED';}
    notify();
  }
  function scenario(code:MissionCode,state:MissionState) {
    if(code==='SHOW_ME')profile=state==='COMPLETED'?{nickname:'가상 참가자',bio:'함께 배워요.',interests:['Python']}:{nickname:'',bio:'',interests:[]};
    if(code==='SYNC_ME')sync=state==='COMPLETED'?{questionnaireVersion:'DEV-DRAFT',type:{...draft.types[0],title:'가상 결과 · '+draft.types[0].title},axisScores:Object.fromEntries(draft.axes.map(axis=>[axis.id,-3])),submittedAt:new Date().toISOString()}:null;
    if(code==='FAST_TAP'){const now=Date.now();tap=['COMPLETED','PENDING'].includes(state)?{attemptId:++counter,generation:1,status:state==='COMPLETED'?'VALID':'REVIEW_REQUIRED',serverNow:new Date(now).toISOString(),startsAt:new Date(now-15000).toISOString(),endsAt:new Date(now-5000).toISOString(),submitDeadline:new Date(now+5000).toISOString(),tapCount:20,reviewReason:null,provisional:true}:null;}
    if(code==='MAKE_A_WISH'||code==='SAY_SOMETHING'){
      if(code==='MAKE_A_WISH')wishLanding=null;
      const kind=code==='MAKE_A_WISH'?'wishes':'cheers';const old=slots[kind].id;feeds[kind]=feeds[kind].filter(x=>x.id!==old);slots[kind]=empty();
      if(state==='PENDING'||state==='COMPLETED'){const id=++counter;const row:Revision={id:id+1,itemId:id,number:1,content:'개발용 상태 전환으로 만든 가상 메시지예요.',templateId:'NOTE',style:'ROSE',anonymous:true,submittedAt:new Date().toISOString(),status:state==='COMPLETED'?'APPROVED':'QUEUED',reason:null};slots[kind]={...empty(),id,version:1,visibility:'VISIBLE',latestRevision:row,pendingRevision:state==='PENDING'?row:null,approvedRevision:state==='COMPLETED'?row:null,missionCompleted:state==='COMPLETED'};if(state==='COMPLETED')feeds[kind].unshift(publicItem(row.content,id));}
    }
    change(code,state);
  }
  return {progress,unlockTag,celebrations,slots,feeds,content,reactions,results,moderate,scenario,get wishLanding(){return wishLanding;},rememberLanding:(value:'statue'|'water')=>{wishLanding=value;},get profile(){return profile;},
    subscribe:(fn:()=>void)=>{listeners.add(fn);return()=>{listeners.delete(fn);};},snapshot:()=>revision,
    refresh:async()=>{notify();},change,fail:()=>{failNext=true;},emptyFeed:(kind:ContentKind)=>{feeds[kind]=[];notify();},
    saveProfile:(nickname:string,bio:string,interests:string[],accepted:boolean)=>{if(!nickname.trim()||!bio.trim()||!interests.length||!accepted)throw new Error('닉네임·한 줄 소개·관심 분야와 가상 안내 확인을 모두 채워주세요.');if(nickname.trim().length>30||bio.length>200)throw new Error('닉네임은 30자, 소개는 200자 이내로 적어주세요.');profile={nickname:nickname.trim(),bio,interests:[...interests]};change('SHOW_ME','COMPLETED');},
  };
}
export type Experience = ReturnType<typeof createExperience>;
