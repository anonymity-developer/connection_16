package com.tagme.analytics;

import com.tagme.auth.*;
import com.tagme.operations.OperationsService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminPageEvents {
    public record Window(Instant from,Instant to) {}
    public record EventRow(String ref,Instant eventAt,Instant recordedAt,String screenId,String screenName,
        String eventType,String mission,String step,String participantRef,String sessionRef,String source) {}
    public record Log(Window window,List<EventRow> items,long total,int offset,Integer nextOffset) {}
    public record PageRow(String screenId,String name,long views,long visitors,long repeatVisitors) {}
    public record Day(String date,long views,long visitors) {}
    public record Flow(String fromScreen,String toScreen,long moves,long users) {}
    public record Stage(String name,long users,Double entryConversion,Double previousConversion,Double estimatedDropOff) {}
    public record Funnel(String mission,List<Stage> stages) {}
    public record Summary(Window window,Instant collectionStartedAt,Instant syncCollectionStartedAt,long droppedCompletions,
        List<PageEventCatalog.Screen> screens,List<PageRow> pages,List<Day> days,List<Flow> flows,List<Funnel> funnels) {}
    private final JdbcTemplate db;private final OperationsService operations;private final AuthRateLimiter limits;
    private final CompletionEventRecorder recorder;private final TransactionTemplate reads;
    public AdminPageEvents(JdbcTemplate db,OperationsService operations,AuthRateLimiter limits,CompletionEventRecorder recorder,PlatformTransactionManager manager) {
        this.db=db;this.operations=operations;this.limits=limits;this.recorder=recorder;
        // access() locks and revalidates the operator in this same transaction.
        reads=new TransactionTemplate(manager);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(15);
    }
    // Old SYNC rows remain in V21. Its client timestamp is deliberately not treated as a server clock.
    private static final String EVENTS="""
        (SELECT id,1 AS origin,user_id,session_key,screen_id,event_type,mission,step,source,event_at,recorded_at FROM page_events
         UNION ALL
         SELECT id,0 AS origin,user_id,analytics_session_key,'SYNC_ME','MISSION_STEP_VIEW',mission,
           IF(step=0,'SURVEY',CONCAT('QUESTION_',step)),'SYNC',received_at,received_at FROM mission_funnel_events) e
        """;
    private static final String COHORT="""
         JOIN users u ON u.id=e.user_id JOIN user_credentials c ON c.user_id=u.id
         WHERE u.status='ACTIVE' AND u.role='USER'
           AND NOT EXISTS (SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)
           AND e.recorded_at>=? AND e.recorded_at<?
        """;
    public static Window window(Instant from,Instant to) {
        if(to==null) to=Instant.now();if(from==null) from=to.minus(Duration.ofDays(7));
        if(!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0) throw new AuthException(400,"INVALID_ANALYTICS_PERIOD","조회 기간은 최대 31일이에요.");
        return new Window(from,to);
    }
    private <T>T read(AuthPrincipal actor,Supplier<T> query) {
        limits.check("analytics-admin",Long.toString(actor.userId()),60,60);
        return reads.execute(status->{operations.access(actor);return query.get();});
    }
    public Log log(AuthPrincipal actor,Window w,String screen,String type,int offset,int limit) {
        if(offset<0||offset>100000||limit<1||limit>50) throw PageEventCatalog.invalid();
        if(screen!=null) PageEventCatalog.screen(screen);
        if(type!=null&&!Set.of("PAGE_VIEW","MISSION_STEP_VIEW","MISSION_SUBMIT_SUCCESS").contains(type)) throw PageEventCatalog.invalid();
        return read(actor,()->{
            String sql=" FROM "+EVENTS+COHORT;var args=new ArrayList<Object>(List.of(Timestamp.from(w.from()),Timestamp.from(w.to())));
            if(screen!=null){sql+=" AND e.screen_id=?";args.add(screen);}if(type!=null){sql+=" AND e.event_type=?";args.add(type);}
            long total=db.queryForObject("SELECT COUNT(*)"+sql,Long.class,args.toArray());
            var rowsArgs=new ArrayList<>(args);rowsArgs.add(limit);rowsArgs.add(offset);
            var rows=db.query("SELECT e.*"+sql+" ORDER BY e.recorded_at DESC,e.origin DESC,e.id DESC LIMIT ? OFFSET ?",(r,n)->new EventRow(
                (r.getInt("origin")==1?"PAGE:":"SYNC:")+r.getLong("id"),r.getTimestamp("event_at").toInstant(),r.getTimestamp("recorded_at").toInstant(),
                r.getString("screen_id"),PageEventCatalog.screen(r.getString("screen_id")).name(),r.getString("event_type"),r.getString("mission"),r.getString("step"),
                "P-"+r.getLong("user_id"),r.getString("session_key")==null?null:r.getString("session_key").substring(0,16),r.getString("source")),rowsArgs.toArray());
            int next=offset+rows.size();return new Log(w,rows,total,offset,next<total&&next<=100000?next:null);
        });
    }
    public Summary summary(AuthPrincipal actor,Window w) {
        return read(actor,()->{
            Object[] args={Timestamp.from(w.from()),Timestamp.from(w.to())};
            String pages=" FROM page_events e"+COHORT+" AND e.event_type='PAGE_VIEW'";
            var pageRows=db.query("SELECT screen_id,SUM(n) views,COUNT(*) visitors,SUM(n>1) repeats FROM (SELECT e.screen_id,e.user_id,COUNT(*) n"+pages+" GROUP BY e.screen_id,e.user_id) p GROUP BY screen_id ORDER BY views DESC,screen_id",
                (r,n)->new PageRow(r.getString(1),PageEventCatalog.screen(r.getString(1)).name(),r.getLong(2),r.getLong(3),r.getLong(4)),args);
            var days=db.query("SELECT DATE(e.recorded_at+INTERVAL 9 HOUR) day,COUNT(*),COUNT(DISTINCT e.user_id)"+pages+" GROUP BY day ORDER BY day",(r,n)->new Day(r.getString(1),r.getLong(2),r.getLong(3)),args);
            // A lost previous event breaks the edge. Never bridge across gaps or other sessions/users.
            var flows=db.query("SELECT p.screen_id,e.screen_id,COUNT(*),COUNT(DISTINCT e.user_id) FROM page_events e JOIN page_events p ON p.event_key=e.parent_page_key AND p.source='CLIENT' AND p.event_type='PAGE_VIEW' AND p.user_id=e.user_id AND p.session_key=e.session_key AND p.id<e.id"+COHORT+" AND e.event_type='PAGE_VIEW' GROUP BY p.screen_id,e.screen_id ORDER BY COUNT(*) DESC,p.screen_id,e.screen_id LIMIT 20",
                (r,n)->new Flow(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4)),args);
            var counts=db.queryForList("SELECT e.mission,e.event_type,e.step,COUNT(DISTINCT e.user_id) users FROM "+EVENTS+COHORT+" AND e.mission IS NOT NULL GROUP BY e.mission,e.event_type,e.step",args);
            var funnels=new ArrayList<Funnel>();
            for(String mission:List.of("SHOW_ME","SYNC_ME","MAKE_A_WISH","FAST_TAP","SAY_SOMETHING")) {
                // COUNT(DISTINCT) across all eligible entry screens, rather than summing per-page users.
                long entry=db.queryForObject("SELECT COUNT(DISTINCT e.user_id) FROM page_events e"+COHORT+" AND e.mission=? AND e.event_type='PAGE_VIEW'",Long.class,args[0],args[1],mission);
                var names=new ArrayList<String>();var values=new ArrayList<Long>();names.add("미션 진입");values.add(entry);
                String[] steps=switch(mission){case "SHOW_ME"->new String[]{"FORM"};case "SYNC_ME"->new String[]{"SURVEY"};case "FAST_TAP"->new String[]{"READY","PLAYING","RESULT"};default->new String[]{"COMPOSE"};};
                for(String step:steps) {
                    names.add(switch(step){case "FORM"->"프로필 입력";case "SURVEY"->"설문 시작";case "READY"->"게임 준비";case "PLAYING"->"공식 게임 진행";case "RESULT"->"결과 표시";default->"작성 화면";});
                    values.add(counts.stream().filter(row->mission.equals(row.get("mission"))&&"MISSION_STEP_VIEW".equals(row.get("event_type"))&&step.equals(row.get("step"))).mapToLong(row->((Number)row.get("users")).longValue()).sum());
                }
                names.add("서버 저장 완료");values.add(counts.stream().filter(row->mission.equals(row.get("mission"))&&"MISSION_SUBMIT_SUCCESS".equals(row.get("event_type"))).mapToLong(row->((Number)row.get("users")).longValue()).sum());
                var stages=new ArrayList<Stage>();
                for(int i=0;i<values.size();i++){long value=values.get(i);Double previous=i==0?null:ratio(value,values.get(i-1));stages.add(new Stage(names.get(i),value,ratio(value,entry),previous,previous==null?null:100-previous));}
                funnels.add(new Funnel(mission,stages));
            }
            return new Summary(w,db.queryForObject("SELECT started_at FROM page_event_collection WHERE id=1",Timestamp.class).toInstant(),
                db.queryForObject("SELECT started_at FROM funnel_collection WHERE id=1",Timestamp.class).toInstant(),recorder.dropped(),PageEventCatalog.SCREENS,pageRows,days,flows,funnels);
        });
    }
    private static Double ratio(long n,long d){return d==0?null:100.0*n/d;}
}
