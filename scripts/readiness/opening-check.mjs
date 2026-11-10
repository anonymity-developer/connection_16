import { readFile } from 'node:fs/promises';
import { basename, extname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { audit, probe } from './audit.mjs';
import { phase, utcMicros } from './time.mjs';
import { validateQuestionnaire } from './questionnaire.mjs';
const codes=['SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING'];
const unknown=(id,action)=>({id,status:'NOT_VERIFIED',evidence:'이번 실행에서 실환경 확인 없음',action});
export function diagnose(snapshot, now=new Date().toISOString()) {
  const checks=[];
  const add=(id,status,evidence,action)=>checks.push({id,status,evidence,action});
  const date=utcMicros(now);
  if(date===null)throw new Error('Invalid observation time');
  const supplied=snapshot!==null && typeof snapshot==='object' && !Array.isArray(snapshot);
  if(supplied){
    // Only known metadata is consumed. Secret-bearing snapshots are rejected, never echoed.
    const forbidden=/(?:password|secret|token|hash|credential|cookie|otp|mattermost.?id|real.?name|content.?text)/i;
    const inspect=(v,depth=0)=>{if(depth>12)throw new Error('Metadata nesting too deep');if(v&&typeof v==='object')for(const [key,value] of Object.entries(v)){if(forbidden.test(key))throw new Error('Forbidden metadata field');inspect(value,depth+1);}};
    inspect(snapshot);
    const captured=utcMicros(snapshot.capturedAt);
    add('snapshot-time',captured===null?'NOT_VERIFIED':captured>date?'BLOCKED':'READY',captured===null?'자료 생성 시각 없음':`자료 생성 이후 경과 ${Number(date-captured)/1000000}초; 자료 갱신 여부는 자동 입증하지 않음`,'실제 설정 변경 이후 새 읽기 전용 자료로 확인. 미래 시각이면 서버/PC UTC 확인');
    const event=snapshot.event;
    const legacy=audit(snapshot,new Date(now));
    for(const c of legacy.checks.filter(c=>!['event-open','sync-config','missions'].includes(c.id)))add(c.id,c.ok?'READY':'BLOCKED',c.message,'설정 메타데이터를 기존 서버 요구 조건과 대조. READY는 입력 자료 범위만 의미');
    const state=phase(event,now);
    add('event-state',state==='OPEN'?'READY':state==='BEFORE_OPEN'?'NEEDS_APPROVAL':'BLOCKED',`현재 검사 시각의 상태 ${state}`,state==='BEFORE_OPEN'?'오픈 직전 경계에 다시 확인; 사전 준비는 미션 참여가 아님':'종료/미설정이면 신규 참여 불가');
    add('profile-preparation',event && [true,false,0,1].includes(event.profilePreparationEnabled)?'READY':'BLOCKED','사전 프로필 옵션의 불리언 형식 검사','사전 등록 허용 여부는 운영진이 별도 승인');
    const missions=snapshot.missions;
    add('mission-catalog',Array.isArray(missions)&&missions.length===5&&codes.every(c=>missions.filter(m=>m.code===c&&(m.active===true||m.active===1)).length===1)?'READY':'BLOCKED','정확히 5종 활성 미션 메타데이터','코드/활성 상태 확인. SHOW ME는 NFC 불필요');
    for(const code of codes.slice(1)){
      const rows=Array.isArray(snapshot.nfc)?snapshot.nfc.filter(t=>t.missionCode===code):[];
      add(`nfc-${code}`,rows.length===1&&Number.isInteger(rows[0].activeCount)&&rows[0].activeCount>0?'READY':'BLOCKED','활성 태그 건수 집계만 사용; 토큰/해시는 취급하지 않음','해당 미션의 승인된 태그 등록 및 실물/QR 동작은 별도 확인');
    }
    add('nfc-integrity',snapshot.nfcIntegrity?.invalidDigestCount===0&&snapshot.nfcIntegrity?.duplicateDigestCount===0?'READY':'BLOCKED','SQL의 형식 오류·중복 건수만 검사','형식/중복 검사이며 비밀 URL과 해시의 일치·실제 스캔을 입증하지 않음');
    const q=snapshot.questionnaire;
    const version=q?.version;const validVersion=typeof version==='string'&&/^[A-Za-z0-9._-]{1,64}$/.test(version);
    const inspected=validateQuestionnaire(q?.definition);
    add('sync-definition',snapshot.currentQuestionnaires===1&&validVersion&&inspected.valid?'READY':'BLOCKED',inspected.valid?`문항 ${inspected.questionCount}, 축 ${inspected.axisCount}, 유형 ${inspected.typeCount}; 구조 검사`:'설문 포인터/버전/문항/유형 구조 확인 실패','서버 Questionnaire.validate가 최종 판정. 운영 문항 승인·실제 제출/S 지급은 미검증');
  } else checks.push(unknown('configuration-snapshot','읽기 전용 SQL이 반환하는 비밀정보 없는 설정 자료가 필요. Work에서는 운영 DB 조회 안 함'));
  for(const [id,action] of [
    ['event-policy-approval','일정·사전 준비 옵션 운영 승인'],['consent-approval','두 정책의 버전·공개 범위·보관/철회·문의 승인. 가입 전 개인정보 안내 누락의 승인된 해결 방안 필요'],['sync-approval','기존 예시 6문항은 초안. 운영자 확정 버전 승인'],['sender-approval','전용 MM 발신 계정/채널 조회·DM 권한 승인. 발신자는 본인 OTP를 받을 수 없음'],['moderation-coverage','제공자 미설정 시 수동 검수 인력·근무/마감 절차 승인']
  ])add(id,'NEEDS_APPROVAL','코드/설정 자료로 운영 승인을 대신할 수 없음',action);
  for(const [id,action] of [
    ['mattermost-live','승인된 계정의 실제 로그인/채널 검증/본인 테스트 DM은 별도 승인된 리허설에서만'],['admin-live','현재 운영자 계정으로 권한·검수 큐·시상/결과 메뉴 확인'],['storage-live','Spring/MySQL/Redis 실제 기동·세션·장애 복구 확인'],['nfc-device','정확한 위치 확정 후 네 NFC/QR·로그인 복귀 실기기 확인'],['mission-rehearsal','프로필 S 자동 지급·나머지4종·검수 후 A/Y·공식5+10초·전체 수집판 실환경 확인']
  ])checks.push(unknown(id,action));
  const status=checks.some(c=>c.status==='BLOCKED')?'BLOCKED':checks.some(c=>c.status==='NOT_VERIFIED')?'NOT_VERIFIED':checks.some(c=>c.status==='NEEDS_APPROVAL')?'NEEDS_APPROVAL':'READY';
  const window=supplied&&snapshot.event?Object.fromEntries(['opensAt','submitClosesAt','moderationClosesAt'].map(key=>{const value=snapshot.event[key];return [key,utcMicros(value)===null?null:{utc:value,kst:new Intl.DateTimeFormat('ko-KR',{dateStyle:'short',timeStyle:'medium',timeZone:'Asia/Seoul'}).format(new Date(value))}];})):null;
  return {schemaVersion:'opening-v1',observedAt:now,window,scope:supplied?'OFFLINE_METADATA_ONLY':'WORK_WITHOUT_LIVE_CONFIGURATION',overall:status,decision:'NO_GO_PENDING_EVIDENCE',automaticGo:false,checks};
}
export function attachProbe(report,rows) {
  const copy={...report,checks:[...report.checks],publicProbe:rows};
  for(const row of rows){
    const ok=row.httpStatus===200;
    let status='NOT_VERIFIED';
    if(row.endpoint==='/api/v1/auth/config')status=ok&&row.available===true?'READY':'BLOCKED';
    if(row.endpoint==='/actuator/health')status=ok&&row.status==='UP'?'READY':'BLOCKED';
    if(row.endpoint==='/api/v1/event')status=ok&&row.status==='OPEN'?'READY':ok&&row.status==='BEFORE_OPEN'?'NEEDS_APPROVAL':'BLOCKED';
    copy.checks.push({id:`local-public-${row.endpoint.split('/').at(-1)}`,status,evidence:`익명 loopback GET HTTP ${row.httpStatus}; 공개 응답의 허용 필드만 검사`,action:'해당 로컬 공개 endpoint의 필요 조건만 확인. 실제 MM 권한/실기기/운영 서버 검증 아님'});
  }
  if(copy.checks.some(c=>c.status==='BLOCKED'))copy.overall='BLOCKED';
  return copy;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  try{
    const args=process.argv.slice(2);let snapshot=null,origin=null;
    while(args.length){const flag=args.shift(),value=args.shift();if(flag==='--snapshot'&&value&&snapshot===null){if(extname(value).toLowerCase()!=='.json'||basename(value).startsWith('.env'))throw new Error('Sanitized JSON metadata required');const text=await readFile(value,'utf8');if(text.length>200000)throw new Error('Oversized metadata');snapshot=JSON.parse(text.replace(/^\uFEFF/,''));}else if(flag==='--local-origin'&&value&&!origin)origin=value;else throw new Error('Unsupported arguments');}
    let report=diagnose(snapshot);if(origin){try{report=attachProbe(report,await probe(origin));}catch{report.checks.push(unknown('local-public-probe','loopback 공개 GET 연결/응답을 확인하지 못함. 인증·DB 변경 없음'));}}
    console.log(JSON.stringify(report,null,2));process.exitCode=2;
  }catch{console.error('Opening preflight failed. Check sanitized metadata format. No credentials, input values or response bodies are printed.');process.exitCode=1;}
}
