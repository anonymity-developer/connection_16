package com.tagme.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import com.tagme.participation.ParticipationModels.MissionCode;
import com.tagme.results.TapRankingService;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberControlService {
    public record ChangeRequest(@PositiveOrZero long expectedGeneration,@NotBlank @Size(max=300) String reason,
        @NotNull @Pattern(regexp="APPLY|DEACTIVATE") String confirmation,@NotNull @Pattern(regexp="UNLOCK|LOCK|GRANT|REVOKE|DEACTIVATE") String action) { }
    private final JdbcTemplate db;
    private final AuthRepository accounts;
    private final ParticipationRepository participation;
    private final OperationsService operations;
    private final ParticipationResetService resets;
    private final AdminAuditRepository audit;
    private final AuthRateLimiter limits;
    private final TapRankingService tap;
    private final ObjectMapper json;
    public MemberControlService(JdbcTemplate db,AuthRepository accounts,ParticipationRepository participation,OperationsService operations,
        ParticipationResetService resets,AdminAuditRepository audit,AuthRateLimiter limits,TapRankingService tap,ObjectMapper json) {
        this.db=db;this.accounts=accounts;this.participation=participation;this.operations=operations;this.resets=resets;this.audit=audit;this.limits=limits;this.tap=tap;this.json=json;
    }
    private AuthException error(int status,String code,String message){return new AuthException(status,code,message);}
    private Object normalized(Object value) {
        if(value instanceof Timestamp time)return time.toInstant().toString();
        if(value instanceof java.time.LocalDateTime time)return time.toInstant(java.time.ZoneOffset.UTC).toString();
        if(value instanceof Map<?,?> source){Map<String,Object> result=new LinkedHashMap<>();source.forEach((k,v)->result.put(k.toString(),normalized(v)));return result;}
        if(value instanceof List<?> source)return source.stream().map(this::normalized).toList();
        return value;
    }
    private String encode(Object value){try{return json.writeValueAsString(normalized(value));}catch(Exception failure){throw new IllegalStateException("Cannot archive member change",failure);}}
    @Transactional
    public ParticipationResetService.ResetRecord change(AuthPrincipal actor,long user,String missionCode,String key,ChangeRequest input) {
        boolean deactivate=missionCode==null;
        if(user<1||key==null||!key.matches("[A-Za-z0-9_-]{16,64}")||input.action()==null||input.reason()==null||input.reason().isBlank()||input.reason().length()>300||input.expectedGeneration()<0
            ||!(deactivate?"DEACTIVATE".equals(input.action())&&"DEACTIVATE".equals(input.confirmation()):List.of("UNLOCK","LOCK","GRANT","REVOKE").contains(input.action())&&"APPLY".equals(input.confirmation())))
            throw error(400,"INVALID_MEMBER_CHANGE","변경 대상과 사유, 확인을 입력해 주세요.");
        MissionCode code=null;
        if(!deactivate){try{code=MissionCode.valueOf(missionCode);}catch(IllegalArgumentException invalid){throw error(400,"INVALID_MISSION","미션을 확인해 주세요.");}}
        java.util.stream.LongStream.of(actor.userId(),user).distinct().sorted().forEach(accounts::lockUser);
        participation.lockPrincipal(actor);operations.access(actor);
        var target=accounts.byId(user).orElseThrow(()->error(404,"MEMBER_NOT_FOUND","참가자를 찾을 수 없어요."));
        if(!"USER".equals(target.role())||db.queryForObject("SELECT COUNT(*) FROM local_admin_accounts WHERE user_id=?",Integer.class,user)>0)
            throw error(403,"ADMIN_TARGET_FORBIDDEN","관리자 계정은 변경 대상이 아니에요.");
        var replay=db.queryForList("SELECT * FROM game_participation_resets WHERE actor_id=? AND request_key=?",actor.userId(),key);
        if(!replay.isEmpty()) {
            var old=replay.getFirst();
            if(((Number)old.get("user_id")).longValue()!=user||((Number)old.get("generation")).longValue()!=input.expectedGeneration()+1
                ||!input.action().equals(old.get("operation"))||!Objects.equals(missionCode,old.get("mission_code"))||!input.reason().strip().equals(old.get("reason")))
                throw error(409,"IDEMPOTENCY_CONFLICT","같은 요청의 대상이나 내용이 바뀌었어요.");
            return readRecord(((Number)old.get("id")).longValue());
        }
        if(!"ACTIVE".equals(target.status()))throw error(409,"MEMBER_INACTIVE","이미 비활성화된 참가자예요.");
        participation.requireGeneration(user,input.expectedGeneration());
        limits.check("admin-member-change",Long.toString(actor.userId()),30,60);
        tap.lockForReset();
        String missionFilter=deactivate?"":" AND m.code=?";
        Object[] params=deactivate?new Object[]{user}:new Object[]{user,code.name()};
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("schemaVersion",1);snapshot.put("userId",user);snapshot.put("operation",input.action());snapshot.put("missionCode",missionCode);
        snapshot.put("progress",db.queryForList("SELECT p.* FROM mission_progress p JOIN missions m ON m.id=p.mission_id WHERE p.user_id=?"+missionFilter,params));
        snapshot.put("evidence",db.queryForList("SELECT e.* FROM mission_completion_evidence e JOIN missions m ON m.id=e.mission_id WHERE e.user_id=?"+missionFilter,params));
        snapshot.put("completion",db.queryForList("SELECT * FROM event_completions WHERE user_id=?",user));
        snapshot.put("unlockReceipts",deactivate?db.queryForList("SELECT * FROM submission_receipts WHERE user_id=? AND source_kind='TAG_UNLOCK'",user):db.queryForList("SELECT r.* FROM submission_receipts r JOIN missions m ON m.id=r.source_id WHERE r.user_id=? AND r.source_kind='TAG_UNLOCK' AND m.code=?",user,code.name()));
        if(deactivate||code==MissionCode.SYNC_ME){snapshot.put("sync",db.queryForList("SELECT * FROM sync_results WHERE user_id=?",user));snapshot.put("syncReceipts",db.queryForList("SELECT * FROM submission_receipts WHERE user_id=? AND source_kind='SYNC_RESULT'",user));}
        if(deactivate||code==MissionCode.FAST_TAP){snapshot.put("tapSlots",db.queryForList("SELECT * FROM tap_slots WHERE user_id=?",user));snapshot.put("tapAttempts",db.queryForList("SELECT * FROM tap_attempts WHERE user_id=? AND archived_at IS NULL",user));}
        String contentFilter=deactivate?"":" AND kind IN ("+(code==MissionCode.MAKE_A_WISH?"'WISH','COMMENT'":code==MissionCode.SAY_SOMETHING?"'CHEER'":"'NONE'")+")";
        snapshot.put("content",db.queryForList("SELECT * FROM content_items WHERE author_id=? AND archived_at IS NULL"+contentFilter,user));
        if(deactivate||code==MissionCode.SHOW_ME)snapshot.put("profile",db.queryForList("SELECT * FROM profiles WHERE user_id=?",user));
        if(deactivate){
            snapshot.put("interests",db.queryForList("SELECT * FROM user_interests WHERE user_id=?",user));
            snapshot.put("relationships",db.queryForList("SELECT * FROM profile_views WHERE viewer_id=? OR viewed_id=?",user,user));
            snapshot.put("likes",db.queryForList("SELECT * FROM cheer_likes WHERE user_id=?",user));
        }
        long next=input.expectedGeneration()+1;
        Map<String,Object> summary=Map.of("tagsPreserved",!deactivate&&!"LOCK".equals(input.action()),"showMePreserved",!deactivate&&code!=MissionCode.SHOW_ME);
        var keys=new GeneratedKeyHolder();
        db.update(c->{var p=c.prepareStatement("INSERT INTO game_participation_resets(user_id,actor_id,generation,request_key,reason,snapshot_json,summary_json,operation,mission_code) VALUES (?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            p.setLong(1,user);p.setLong(2,actor.userId());p.setLong(3,next);p.setString(4,key);p.setString(5,input.reason().strip());p.setString(6,encode(snapshot));p.setString(7,encode(summary));p.setString(8,input.action());p.setString(9,missionCode);return p;},keys);
        long change=Objects.requireNonNull(keys.getKey()).longValue();
        boolean clear=deactivate||List.of("LOCK","REVOKE").contains(input.action());
        if(clear){
            db.update("DELETE e FROM mission_completion_evidence e JOIN missions m ON m.id=e.mission_id WHERE e.user_id=?"+missionFilter,params);
            db.update("DELETE FROM event_completions WHERE user_id=?",user);
            if(deactivate||code==MissionCode.SYNC_ME){db.update("DELETE FROM sync_results WHERE user_id=?",user);db.update("DELETE FROM submission_receipts WHERE user_id=? AND source_kind='SYNC_RESULT'",user);}
            if(deactivate||code==MissionCode.FAST_TAP){db.update("UPDATE tap_attempts SET archived_at=UTC_TIMESTAMP(6) WHERE user_id=? AND archived_at IS NULL",user);db.update("DELETE FROM tap_slots WHERE user_id=?",user);}
            db.update("UPDATE moderation_jobs j JOIN content_revisions r ON r.id=j.revision_id JOIN content_items i ON i.id=r.item_id SET j.status='SUPERSEDED',j.fence=j.fence+1,j.lease_until=NULL WHERE i.id IN (SELECT id FROM (SELECT id FROM content_items WHERE author_id=? AND archived_at IS NULL"+contentFilter+") selected) AND j.status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')",user);
            db.update("UPDATE content_items SET archived_at=UTC_TIMESTAMP(6),visibility_status='HIDDEN',version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE author_id=? AND archived_at IS NULL"+contentFilter,user);
            if(deactivate||"LOCK".equals(input.action())){
                db.update("DELETE p FROM mission_progress p JOIN missions m ON m.id=p.mission_id WHERE p.user_id=?"+missionFilter,params);
                if(deactivate)db.update("DELETE FROM submission_receipts WHERE user_id=? AND source_kind='TAG_UNLOCK'",user);
                else db.update("DELETE r FROM submission_receipts r JOIN missions m ON m.id=r.source_id WHERE r.user_id=? AND r.source_kind='TAG_UNLOCK' AND m.code=?",user,code.name());
            }
            else db.update("UPDATE mission_progress p JOIN missions m ON m.id=p.mission_id SET p.status='UNLOCKED',p.completed_at=NULL WHERE p.user_id=?"+missionFilter,params);
            if(!deactivate&&code==MissionCode.SHOW_ME){
                db.update("INSERT IGNORE INTO show_me_regrant_blocks(user_id) VALUES(?)",user);
                db.update("UPDATE profiles SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=?",user);
            }
        }
        if(deactivate){
            db.update("INSERT INTO account_deactivations(user_id,original_mattermost_id,original_username,change_id) VALUES(?,?,?,?)",user,target.mattermostId(),target.username(),change);
            db.update("DELETE FROM profile_views WHERE viewer_id=? OR viewed_id=?",user,user);db.update("DELETE FROM cheer_likes WHERE user_id=?",user);
            participation.hide(user);
            db.update("UPDATE user_credentials SET credential_version=credential_version+1 WHERE user_id=?",user);
            db.update("UPDATE connection_sessions SET ended_at=LEAST(UTC_TIMESTAMP(6),expires_at),end_reason='REVOKED' WHERE user_id=? AND ended_at IS NULL",user);
            db.update("UPDATE users SET status='WITHDRAWN',mattermost_id=CONCAT('archived:',id),mattermost_username=NULL WHERE id=?",user);
        } else if(List.of("UNLOCK","GRANT").contains(input.action())) {
            var mission=participation.mission(user,code);
            if("LOCKED".equals(mission.state()))participation.unlock(user,mission.id(),participation.now());
            if("GRANT".equals(input.action())&&!"COMPLETED".equals(mission.state())) {
                if(code==MissionCode.SHOW_ME)participation.allowShowMeRegrant(user);
                var now=participation.now();var receipt=participation.receipt(user,"ADMIN_ADJUSTMENT",change,now);
                participation.complete(user,participation.mission(user,code),receipt,now);
            }
        }
        audit.record(actor.userId(),deactivate?"MEMBER_DEACTIVATED":"MISSION_ADJUSTED","USER",user,input.reason(),Map.of("generation",input.expectedGeneration()),Map.of("generation",next,"changeId",change,"action",input.action(),"mission",Objects.toString(missionCode,"ALL")));
        return readRecord(change);
    }
    private ParticipationResetService.ResetRecord readRecord(long id) {
        return db.queryForObject("SELECT * FROM game_participation_resets WHERE id=?",(r,n)->{try{return new ParticipationResetService.ResetRecord(r.getLong("id"),r.getLong("generation"),r.getLong("actor_id"),r.getString("reason"),r.getTimestamp("created_at").toInstant(),json.readTree(r.getString("summary_json")),r.getString("operation"),r.getString("mission_code"));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}},id);
    }
}
