package com.tagme.operations;

import java.util.*;
import static com.tagme.operations.PostEventModels.*;

/** Fixed, minimal columns; participant histories and user text are never exported. */
public final class PostEventExport {
    private PostEventExport() { }
    public static String cell(Object value){String text=value==null?"":String.valueOf(value);String lead=text.stripLeading();if((!lead.isEmpty()&&"=+@-".indexOf(lead.charAt(0))>=0)||text.startsWith("\t")||text.startsWith("\r")||text.startsWith("\n"))text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
    private static void row(StringBuilder out,Object...fields){out.append(Arrays.stream(fields).map(PostEventExport::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");}
    public static String csv(Report report){var out=new StringBuilder("\uFEFF");row(out,"section","key","participant_id","rank","value","status","basis","snapshot_id","observed_at");var d=report.data();
        for(var m:d.metrics())row(out,"SUMMARY",m.key(),"","",m.value(),m.unit(),m.basis(),report.snapshotId(),d.window().observedAt());
        for(var m:d.missions()){row(out,"MISSION_UNLOCKED",m.code(),"","",m.unlocked(),"",m.basis(),report.snapshotId(),d.window().observedAt());row(out,"MISSION_PERFORMED",m.code(),"","",m.performed(),"",m.basis(),report.snapshotId(),d.window().observedAt());row(out,"MISSION_COMPLETED",m.code(),"","",m.completed(),"",m.basis(),report.snapshotId(),d.window().observedAt());row(out,"MISSION_PENDING",m.code(),"","",m.pending(),"",m.basis(),report.snapshotId(),d.window().observedAt());}
        for(var b:d.buckets())row(out,"DISTRIBUTION",b.group(),"","",b.count(),b.label(),"현재 저장 상태 / current snapshot",report.snapshotId(),d.window().observedAt());
        for(var h:d.hours())row(out,"HOUR",h.event(),"","",h.participants(),h.hourUtc(),"distinct participants; records="+h.records(),report.snapshotId(),d.window().observedAt());
        for(var c:d.candidates())row(out,"AWARD_CANDIDATE",c.category(),c.participantId(),c.rank(),c.score(),c.unresolvedTie()?"REVIEW_REQUIRED":"PROVISIONAL","accepted="+c.validCompletionAt()+";profile="+c.profileRegisteredAt()+";metricTie="+c.metricTie()+";overlap="+c.overlappingTopCategories()+";proofs="+c.proofs(),report.snapshotId(),d.window().observedAt());
        for(var r:d.reviewRecords())row(out,"AWARD_EXCLUSION_REVIEW","source="+r.sourceId(),r.participantId(),"","",r.reason(),"not a winner",report.snapshotId(),d.window().observedAt());
        for(var p:d.policyDecisions())row(out,"POLICY","PENDING","","","","NOT_APPROVED",p,report.snapshotId(),d.window().observedAt());
        for(var p:d.limitations())row(out,"LIMITATION","SCOPE","","","","",p,report.snapshotId(),d.window().observedAt());
        row(out,"META","scope","","","",d.phase(),d.scope(),report.snapshotId(),d.window().observedAt());row(out,"META","truncation","","",d.reviewTotal(),"hoursTruncated="+d.hoursTruncated(),"review records capped at 100",report.snapshotId(),d.window().observedAt());return out.toString();
    }
    public static String markdown(Report report){var d=report.data();var out=new StringBuilder("# TAG ME 행사 결과 — 잠정 검토 자료\n\n엘리베이터 대기 시간과 이동 동선에서 NFC를 통해 동료와 연결되는 경험을 목표로 기획했습니다.\n\n조회: "+d.window().observedAt()+"\n\n기준: "+report.ruleVersion()+" / "+report.snapshotId()+"\n\n범위: "+d.scope()+"\n\n## 참여·미션 현황\n\n");for(var m:d.metrics())out.append("- ").append(m.key()).append(": ").append(m.value()).append(" ").append(m.unit()).append(" — ").append(m.basis()).append('\n');
        out.append("\n## 미션별 수행·완료\n\n");long max=d.missions().stream().mapToLong(Mission::performed).max().orElse(0);for(var m:d.missions())out.append("- ").append(m.code()).append(": 수행 ").append(m.performed()).append(" / 완료 ").append(m.completed()).append(" / 검수 대기 ").append(m.pending()).append('\n');out.append("\n최다 수행 미션: ").append(max==0?"집계할 참여 기록 없음":d.missions().stream().filter(m->m.performed()==max).map(Mission::code).collect(java.util.stream.Collectors.joining(", "))).append("\n\n## 시상 검토\n\n잠정 후보 ").append(d.candidates().size()).append("행. 확정 불가: 운영 정책 승인 대기. CSV/JSON에서 근거 확인.\n\n## 운영상 개선점\n\n[운영자가 실제 장애·문의 기록을 확인해 작성]\n\n## 다음 행사 제안\n\n[관찰 지표와 운영 경험에 근거해 작성; 인간관계 개선을 단정하지 않음]\n\n## 미확정 정책\n\n");d.policyDecisions().forEach(p->out.append("- ").append(p).append('\n'));out.append("\n## 집계 한계\n\n");d.limitations().forEach(p->out.append("- ").append(p).append('\n'));return out.toString();}
}
