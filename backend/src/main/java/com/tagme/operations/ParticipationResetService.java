package com.tagme.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ParticipationResetService {
    public record ResetRequest(@PositiveOrZero long expectedGeneration,@NotBlank @Size(max=300) String reason,
                               @NotNull @Pattern(regexp="RESET") String confirmation) { }
    public record ResetRecord(long id,long generation,long actorId,String reason,Instant createdAt,JsonNode summary) { }
    private final JdbcTemplate db;
    private final AuthRepository accounts;
    private final ParticipationRepository participation;
    private final OperationsService operations;
    private final AdminAuditRepository audit;
    private final ObjectMapper json;
    private final AuthRateLimiter limits;
    private final com.tagme.results.TapRankingService tapRankings;
    private final TransactionTemplate tx;
    public ParticipationResetService(JdbcTemplate db,AuthRepository accounts,ParticipationRepository participation,
        OperationsService operations,AdminAuditRepository audit,ObjectMapper json,AuthRateLimiter limits,PlatformTransactionManager manager,com.tagme.results.TapRankingService tapRankings) {
        this.db=db;this.accounts=accounts;this.participation=participation;this.operations=operations;
        this.audit=audit;this.json=json;this.limits=limits;this.tx=new TransactionTemplate(manager);this.tapRankings=tapRankings;
    }
    private AuthException error(int status,String code,String message){return new AuthException(status,code,message);}
    private ResetRecord record(ResultSet r,int n)throws SQLException {
        try{return new ResetRecord(r.getLong("id"),r.getLong("generation"),r.getLong("actor_id"),r.getString("reason"),r.getTimestamp("created_at").toInstant(),json.readTree(r.getString("summary_json")));}
        catch(java.io.IOException invalid){throw new IllegalStateException("Cannot read reset summary",invalid);}
    }
    public List<ResetRecord> history(long user) {
        return db.query("SELECT id,generation,actor_id,reason,created_at,summary_json FROM game_participation_resets WHERE user_id=? ORDER BY generation DESC LIMIT 10",this::record,user);
    }
    private Object normalized(Object value) {
        if(value instanceof Timestamp time)return time.toInstant().toString();
        if(value instanceof java.time.LocalDateTime time)return time.toInstant(java.time.ZoneOffset.UTC).toString();
        if(value instanceof Instant time)return time.toString();
        if(value instanceof Map<?,?> source){Map<String,Object> result=new LinkedHashMap<>();source.forEach((k,v)->result.put(k.toString(),normalized(v)));return result;}
        if(value instanceof List<?> source)return source.stream().map(this::normalized).toList();
        return value;
    }
    private String encode(Object value){try{return json.writeValueAsString(normalized(value));}catch(Exception invalid){throw new IllegalStateException("Cannot archive participation",invalid);}}
    public ResetRecord reset(AuthPrincipal actor,long user,String key,ResetRequest body) {
        operations.access(actor);
        if(user<1||key==null||!key.matches("[A-Za-z0-9_-]{16,64}")||body.expectedGeneration()<0||!"RESET".equals(body.confirmation())||body.reason()==null||body.reason().isBlank()||body.reason().length()>300)
            throw error(400,"INVALID_RESET","초기화 대상과 사유, 확인을 입력해 주세요.");
        limits.check("admin-reset",Long.toString(actor.userId()),10,60);
        return tx.execute(s->{
            java.util.stream.LongStream.of(actor.userId(),user).distinct().sorted().forEach(accounts::lockUser);
            participation.lockPrincipal(actor);
            if(!"ADMIN".equals(accounts.byId(actor.userId()).orElseThrow(AuthException::invalid).role()))throw error(403,"ADMIN_REQUIRED","관리자 권한이 필요해요.");
            if(db.queryForObject("SELECT COUNT(*) FROM users u JOIN user_credentials c ON c.user_id=u.id WHERE u.id=? AND NOT EXISTS(SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)",Integer.class,user)!=1)
                throw error(404,"MEMBER_NOT_FOUND","참가자를 찾을 수 없어요.");
            var replay=db.query("SELECT * FROM game_participation_resets WHERE actor_id=? AND request_key=?",this::record,actor.userId(),key);
            if(!replay.isEmpty()) {
                var previous=replay.getFirst();
                Long owner=db.queryForObject("SELECT user_id FROM game_participation_resets WHERE id=?",Long.class,previous.id());
                if(owner!=user||previous.generation()!=body.expectedGeneration()+1||!previous.reason().equals(body.reason().strip()))
                    throw error(409,"IDEMPOTENCY_CONFLICT","같은 초기화 요청의 대상이나 내용이 바뀌었어요.");
                return previous;
            }
            participation.requireGeneration(user,body.expectedGeneration());
            tapRankings.lockForReset();
            Map<String,Object> snapshot=new LinkedHashMap<>();
            snapshot.put("schemaVersion",1);snapshot.put("userId",user);snapshot.put("previousGeneration",body.expectedGeneration());
            snapshot.put("progress",db.queryForList("SELECT p.* FROM mission_progress p JOIN missions m ON m.id=p.mission_id WHERE p.user_id=? AND m.code<>'SHOW_ME'",user));
            snapshot.put("evidence",db.queryForList("SELECT e.* FROM mission_completion_evidence e JOIN missions m ON m.id=e.mission_id WHERE e.user_id=? AND m.code<>'SHOW_ME'",user));
            snapshot.put("completion",db.queryForList("SELECT * FROM event_completions WHERE user_id=?",user));
            snapshot.put("sync",db.queryForList("SELECT * FROM sync_results WHERE user_id=?",user));
            snapshot.put("syncReceipts",db.queryForList("SELECT * FROM submission_receipts WHERE user_id=? AND source_kind='SYNC_RESULT'",user));
            snapshot.put("tapSlots",db.queryForList("SELECT * FROM tap_slots WHERE user_id=?",user));
            snapshot.put("tapAttempts",db.queryForList("SELECT * FROM tap_attempts WHERE user_id=? AND archived_at IS NULL",user));
            snapshot.put("content",db.queryForList("SELECT * FROM content_items WHERE author_id=? AND archived_at IS NULL",user));
            snapshot.put("outgoingLikes",db.queryForList("SELECT * FROM cheer_likes WHERE user_id=?",user));
            Map<String,Object> summary=new LinkedHashMap<>();
            for(String name:List.of("progress","evidence","completion","sync","tapAttempts","content","outgoingLikes"))summary.put(name,((List<?>)snapshot.get(name)).size());
            summary.put("tagsPreserved",true);summary.put("showMePreserved",true);
            long next=body.expectedGeneration()+1;
            var keys=new GeneratedKeyHolder();
            db.update(c->{var p=c.prepareStatement("INSERT INTO game_participation_resets(user_id,actor_id,generation,request_key,reason,snapshot_json,summary_json) VALUES (?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
                p.setLong(1,user);p.setLong(2,actor.userId());p.setLong(3,next);p.setString(4,key);p.setString(5,body.reason().strip());p.setString(6,encode(snapshot));p.setString(7,encode(summary));return p;},keys);
            long resetId=Objects.requireNonNull(keys.getKey()).longValue();
            // Keep original content, revisions, tap events and jobs as archived history.
            db.update("UPDATE moderation_jobs j JOIN content_revisions r ON r.id=j.revision_id JOIN content_items i ON i.id=r.item_id SET j.status='SUPERSEDED',j.fence=j.fence+1,j.lease_until=NULL WHERE i.author_id=? AND i.archived_at IS NULL AND j.status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')",user);
            db.update("UPDATE content_items SET archived_at=UTC_TIMESTAMP(6),visibility_status='HIDDEN',version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE author_id=? AND archived_at IS NULL",user);
            db.update("UPDATE tap_attempts SET archived_at=UTC_TIMESTAMP(6) WHERE user_id=? AND archived_at IS NULL",user);
            db.update("DELETE FROM tap_slots WHERE user_id=?",user);
            db.update("DELETE e FROM mission_completion_evidence e JOIN missions m ON m.id=e.mission_id WHERE e.user_id=? AND m.code<>'SHOW_ME'",user);
            db.update("DELETE FROM event_completions WHERE user_id=?",user);
            // These current projections are preserved verbatim in the immutable reset snapshot.
            db.update("DELETE FROM sync_results WHERE user_id=?",user);
            db.update("DELETE FROM submission_receipts WHERE user_id=? AND source_kind='SYNC_RESULT'",user);
            db.update("DELETE FROM cheer_likes WHERE user_id=?",user);
            db.update("UPDATE mission_progress p JOIN missions m ON m.id=p.mission_id SET p.status='UNLOCKED',p.completed_at=NULL WHERE p.user_id=? AND m.code<>'SHOW_ME'",user);
            audit.record(actor.userId(),"GAME_PARTICIPATION_RESET","USER",user,body.reason(),Map.of("generation",body.expectedGeneration()),Map.of("generation",next,"resetId",resetId,"summary",summary));
            return db.query("SELECT * FROM game_participation_resets WHERE id=?",this::record,resetId).getFirst();
        });
    }
}
