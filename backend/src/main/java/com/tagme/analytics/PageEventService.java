package com.tagme.analytics;

import com.tagme.auth.*;
import com.tagme.participation.*;
import com.tagme.participation.ParticipationModels.MissionCode;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PageEventService {
    private final JdbcTemplate db;
    private final ParticipationRepository participation;
    private final AuthRateLimiter limits;
    private final TransactionTemplate writes;
    @Value("${tagme.analytics.max-user-events:10000}") private int maxUser;
    @Value("${tagme.analytics.max-stored-events:1000000}") private long maxStored;
    public PageEventService(JdbcTemplate db,ParticipationRepository participation,AuthRateLimiter limits,PlatformTransactionManager manager) {
        this.db=db;this.participation=participation;this.limits=limits;writes=new TransactionTemplate(manager);writes.setTimeout(2);
    }
    public void collect(AuthPrincipal actor,PageEventsController.Event event,long generation,String session) {
        if(!"USER".equals(actor.role())) throw denied();
        if(session==null) throw AuthException.invalid();
        var screen=PageEventCatalog.screen(event.screen());
        boolean page="PAGE_VIEW".equals(event.type());
        if(!page && !"MISSION_STEP_VIEW".equals(event.type())) throw PageEventCatalog.invalid();
        if(page && event.step()!=null || !page && (event.step()==null || !PageEventCatalog.STEPS.getOrDefault(screen.id(),Set.of()).contains(event.step()))) throw PageEventCatalog.invalid();
        if(event.eventKey().equals(event.parentPageKey())) throw PageEventCatalog.invalid();
        limits.check("analytics-global","all",10000,60);limits.check("analytics-user",Long.toString(actor.userId()),240,60);
        writes.executeWithoutResult(status->{
            participation.lockPrincipal(actor);requireParticipant(actor.userId());
            if(!page && screen.mission()!=null) {
                participation.requireGeneration(actor.userId(),generation);
                if(!"SHOW_ME".equals(screen.mission())) {
                    var mission=participation.mission(actor.userId(),MissionCode.valueOf(screen.mission()));
                    participation.activeMission(mission.id());
                    if("LOCKED".equals(mission.state()) || !participation.eligible(actor.userId())) throw denied();
                }
            }
            var previous=db.queryForList("SELECT session_key,screen_id,event_type,step,parent_page_key FROM page_events WHERE user_id=? AND source='CLIENT' AND event_key=?",actor.userId(),event.eventKey());
            if(!previous.isEmpty()) {
                var row=previous.getFirst();
                if(!Objects.equals(row.get("session_key"),session)||!Objects.equals(row.get("screen_id"),screen.id())||!Objects.equals(row.get("event_type"),event.type())||!Objects.equals(row.get("step"),event.step())||!Objects.equals(row.get("parent_page_key"),event.parentPageKey()))
                    throw new AuthException(409,"ANALYTICS_KEY_CONFLICT","이벤트 식별자가 다른 내용에 사용됐어요.");
                return;
            }
            reserve(actor.userId(),"CLIENT");
            var now=participation.now();
            db.update("INSERT INTO page_events(user_id,session_key,event_key,parent_page_key,screen_id,event_type,mission,step,source,event_at,recorded_at) VALUES (?,?,?,?,?,?,?,?,'CLIENT',?,?)",
                actor.userId(),session,event.eventKey(),event.parentPageKey(),screen.id(),event.type(),screen.mission(),event.step(),Timestamp.from(now),Timestamp.from(now));
        });
    }
    public void committed(MissionCompletedNotice notice,String session) {
        writes.executeWithoutResult(status->{
            // This runs AFTER the business commit in a separate worker/transaction.
            participation.lockUser(notice.userId());requireParticipant(notice.userId());
            var start=db.queryForObject("SELECT started_at FROM page_event_collection WHERE id=1",Timestamp.class).toInstant();
            if(notice.completedAt().isBefore(start)) return;
            String key="receipt-"+notice.receiptId();
            if(db.queryForObject("SELECT COUNT(*) FROM page_events WHERE user_id=? AND source='SERVER' AND event_key=?",Integer.class,notice.userId(),key)>0) return;
            reserve(notice.userId(),"SERVER");
            db.update("INSERT INTO page_events(user_id,session_key,event_key,screen_id,event_type,mission,source,event_at,recorded_at) VALUES (?,?,?,?,'MISSION_SUBMIT_SUCCESS',?,'SERVER',?,?)",
                notice.userId(),session,key,PageEventCatalog.missionScreen(notice.mission()),notice.mission(),Timestamp.from(notice.completedAt()),Timestamp.from(participation.now()));
        });
    }
    private void requireParticipant(long user) {
        if(db.queryForObject("SELECT COUNT(*) FROM users u JOIN user_credentials c ON c.user_id=u.id WHERE u.id=? AND u.status='ACTIVE' AND u.role='USER' AND NOT EXISTS(SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)",Integer.class,user)!=1) throw denied();
    }
    private void reserve(long user,String source) {
        long count=db.queryForObject("SELECT COUNT(*) FROM page_events WHERE user_id=? AND source=?",Long.class,user,source);
        long stored=db.queryForObject("SELECT stored_events FROM page_event_collection WHERE id=1 FOR UPDATE",Long.class);
        if(count>=("SERVER".equals(source)?500:maxUser)||stored>=maxStored) throw new AuthException(429,"ANALYTICS_STORAGE_LIMIT","이벤트 수집 상한에 도달했어요.");
        db.update("UPDATE page_event_collection SET stored_events=stored_events+1 WHERE id=1");
    }
    private static AuthException denied() { return new AuthException(403,"ANALYTICS_PARTICIPANT_REQUIRED","참가자 화면 이벤트만 수집해요."); }
}
