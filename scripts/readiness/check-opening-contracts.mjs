import {readFile,access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve} from 'node:path';
import {createPlan} from '../rehearsal/plan.mjs';
const root=fileURLToPath(new URL('../../',import.meta.url));
const read=p=>readFile(resolve(root,p),'utf8');
const checks=[];function add(id,ok,evidence){checks.push({id,result:ok?'PASS':'FAIL',scope:'STATIC_SOURCE_ONLY',evidence});}
const [model,frontend,controller,show,tags,sql]=await Promise.all(['frontend/src/features/missions/model.ts','frontend/src/features/operations/results-api.ts','backend/src/main/java/com/tagme/operations/PostEventController.java','backend/src/main/java/com/tagme/participation/ParticipationController.java','scripts/create-tag-bundle.mjs','backend/src/test/resources/post-event/activity-extract.sql'].map(read));
add('display-name',model.includes("name: 'YOU GOT THIS'")&&model.includes("name: 'A WISH COME TRUE'")&&!model.includes("name: 'SAY SOMETHING'")&&!model.includes("name: 'SAY 파이팅!'"),'공식 프론트 model 표시명');
add('results-routes',frontend.includes('/admin/results')&&controller.includes('/api/v1/admin/results')&&controller.includes('/export')&&controller.includes('/participants/{id}'),'결과/다운로드/제한 조회 선언 경로');
add('show-me-reconcile',show.includes('/show-me/reconcile'),'SHOW ME 서버 재확인 선언');
add('four-tag-codes',tags.includes("const codes = ['SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING']"),'발급대상 네 코드 선언. 발급 CLI 실행 아님');
add('analysis-no-show-nfc',sql.includes("m.code<>'SHOW_ME'")&&sql.includes('HEART_CURRENT')&&sql.includes('TAP_SCHEDULED'),'분석 SQL 이벤트 정의 선언');
const pkg=JSON.parse(await read('frontend/package.json'));const plan=createPlan({backend:true,browser:true,fullRegression:true});
for(const suite of plan.browserSuites)add(`npm-${suite}`,typeof pkg.scripts[suite]==='string','기존 npm script 존재');
for(const script of plan.browserScripts){let exists=true;try{await access(resolve(root,'frontend',script));}catch{exists=false;}add(`browser-${script}`,exists,'기존 브라우저 script 파일 존재');}
for(const cls of plan.backendClasses){let exists=true;try{await access(resolve(root,'backend/src/test/java',cls.replaceAll('.','/')+'.java'));}catch{exists=false;}add(`java-${cls.split('.').at(-1)}`,exists,'기존 Java 클래스 파일 존재');}
console.log(JSON.stringify({scope:'Static declarations/file existence; NOT runtime integration',checks},null,2));if(checks.some(c=>c.result==='FAIL'))process.exitCode=1;
