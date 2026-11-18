package com.tagme.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.operations.OperationsService;
import com.tagme.participation.ParticipationRepository;
import com.tagme.participation.ParticipationModels.MissionCode;
import com.tagme.results.Questionnaire;
import com.tagme.results.ResultsRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FunnelService {
    public record Step(String eventType, int step, long users, Double dropOffRate, Double conversionRate) {}
    public record Report(String mission, String questionnaireVersion, Instant collectionStartedAt,
                         Instant generatedAt, List<Step> steps) {}
    private final JdbcTemplate db;
    private final ParticipationRepository participation;
    private final ResultsRepository results;
    private final ObjectMapper json;
    private final AuthRateLimiter limits;
    private final OperationsService operations;
    private final TransactionTemplate writes, reads;

    public FunnelService(JdbcTemplate db, ParticipationRepository participation, ResultsRepository results,
            ObjectMapper json, AuthRateLimiter limits, OperationsService operations, PlatformTransactionManager manager) {
        this.db=db; this.participation=participation; this.results=results; this.json=json;
        this.limits=limits; this.operations=operations;
        writes=new TransactionTemplate(manager); writes.setTimeout(2);
        reads=new TransactionTemplate(manager);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public void collect(AuthPrincipal actor, FunnelController.Event event, long generation) {
        collect(actor,event,generation,null);
    }
    public void collect(AuthPrincipal actor, FunnelController.Event event, long generation,String session) {
        if (!"USER".equals(actor.role())) throw error(403,"PARTICIPANT_REQUIRED");
        limits.check("funnel-user",Long.toString(actor.userId()),120,60);
        limits.check("analytics-global","all",10000,60);
        // A separate request/transaction: telemetry errors never roll back a mission submission.
        writes.executeWithoutResult(status -> {
            participation.lockPrincipal(actor);
            if (db.queryForObject("SELECT COUNT(*) FROM users u WHERE u.id=? AND u.role='USER' AND NOT EXISTS (SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)",Integer.class,actor.userId()) != 1)
                throw error(403,"PARTICIPANT_REQUIRED");
            participation.requireGeneration(actor.userId(),generation);
            participation.requireOpen(false);
            var mission=participation.mission(actor.userId(),MissionCode.SYNC_ME);
            participation.activeMission(mission.id());
            if ("LOCKED".equals(mission.state())) throw error(403,"MISSION_LOCKED");
            if (!participation.eligible(actor.userId())) throw error(403,"PROFILE_REQUIRED");
            var definition=results.currentDefinition();
            if (definition == null || !definition.version().equals(event.questionnaireVersion())) throw error(409,"QUESTIONNAIRE_CHANGED");
            int count=Questionnaire.parse(definition.json(),json).questions().size();
            if (!("SCREEN_ENTER".equals(event.eventType()) && event.step()==0)
                    && !("QUESTION_VIEW".equals(event.eventType()) && event.step()>=1 && event.step()<=count)) throw error(400,"INVALID_FUNNEL_STEP");
            Instant now=participation.now();
            if (event.occurredAt().isBefore(now.minusSeconds(86400)) || event.occurredAt().isAfter(now.plusSeconds(300))) throw error(400,"INVALID_FUNNEL_TIME");
            // The user lock makes both the lifetime cap and idempotency safe under concurrent requests.
            if (db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events WHERE user_id=? AND event_key=?",Integer.class,actor.userId(),event.eventKey())>0) return;
            if (db.queryForObject("SELECT COUNT(*) FROM mission_funnel_events WHERE user_id=?",Integer.class,actor.userId())>=2000) throw error(429,"FUNNEL_LIMIT_REACHED");
            db.update("INSERT INTO mission_funnel_events(user_id,mission,questionnaire_version,generation,event_key,event_type,step,occurred_at,received_at,analytics_session_key) VALUES (?,'SYNC_ME',?,?,?,?,?,?,?,?)",
                actor.userId(),definition.version(),generation,event.eventKey(),event.eventType(),event.step(),Timestamp.from(event.occurredAt()),Timestamp.from(now),session);
        });
    }

    public Report report(AuthPrincipal actor) {
        limits.check("funnel-admin",Long.toString(actor.userId()),30,60);
        return reads.execute(status -> {
            operations.access(actor);
            Instant since=db.queryForObject("SELECT started_at FROM funnel_collection WHERE id=1",Timestamp.class).toInstant();
            Instant now=participation.now();
            // Non-locking read keeps the questionnaire in the same MVCC snapshot as the counts.
            var definition=db.query("SELECT q.version,q.definition_json FROM current_sync_questionnaire c JOIN sync_questionnaires q ON q.version=c.version WHERE c.id=1",
                (r,n)->new ResultsRepository.Definition(r.getString(1),r.getString(2))).stream().findFirst().orElse(null);
            if (definition==null) return new Report("SYNC_ME",null,since,now,List.of());
            int count=Questionnaire.parse(definition.json(),json).questions().size();
            long[] counts=new long[count+2];
            // Current active participants and current reset generation only; version changes never mix questions.
            db.query("""
                SELECT e.step,COUNT(DISTINCT e.user_id) AS users FROM mission_funnel_events e
                JOIN users u ON u.id=e.user_id
                WHERE e.questionnaire_version=? AND u.status='ACTIVE' AND u.role='USER'
                  AND NOT EXISTS (SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)
                  AND e.generation=(SELECT COALESCE(MAX(r.generation),0) FROM game_participation_resets r WHERE r.user_id=u.id)
                GROUP BY e.step
                """, r -> { int step=r.getInt("step"); if (step<=count) counts[step]=r.getLong("users"); },definition.version());
            // This existing row is visible only after the real submission transaction commits.
            // No analytics write, client completion event or inferred completion can affect this count.
            counts[count+1]=db.queryForObject("""
                SELECT COUNT(*) FROM sync_results s JOIN users u ON u.id=s.user_id
                WHERE s.questionnaire_version=? AND s.submitted_at>=? AND u.status='ACTIVE' AND u.role='USER'
                  AND NOT EXISTS (SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)
                """,Long.class,definition.version(),Timestamp.from(since));
            return new Report("SYNC_ME",definition.version(),since,now,steps(counts));
        });
    }

    static List<Step> steps(long[] counts) {
        var rows=new ArrayList<Step>();
        for (int i=0;i<counts.length;i++) rows.add(new Step(i==0?"SCREEN_ENTER":i==counts.length-1?"SUBMIT_SUCCESS":"QUESTION_VIEW",i,counts[i],
            i==0 || counts[i-1]==0 ? null : 100.0*(counts[i-1]-counts[i])/counts[i-1],
            counts[0]==0 ? null : 100.0*counts[i]/counts[0]));
        return List.copyOf(rows);
    }
    private static AuthException error(int status,String code) { return new AuthException(status,code,"분석 이벤트를 기록하지 못했어요."); }
}
