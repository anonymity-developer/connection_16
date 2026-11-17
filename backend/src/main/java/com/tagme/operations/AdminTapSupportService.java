package com.tagme.operations;

import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import com.tagme.participation.ParticipationModels.MissionCode;
import com.tagme.results.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTapSupportService {
    public record RetryRequest(@Positive long attemptId,@PositiveOrZero long expectedGeneration,
        @NotBlank @Size(max=300) String reason,@NotNull @Pattern(regexp="RETRY") String confirmation) { }
    public record Restoration(String originalStatus,String originalReason,Instant originalDeadline,Instant effectiveDeadline,
        Instant recognizedAt,Instant restoredAt,long actorId,String operatorId,String reason) { }
    public record Attempt(long userId,String username,String nickname,String realName,String region,Integer classNo,
        long attemptId,String status,String reason,Integer tapCount,Instant startsAt,Instant submitDeadline,
        Instant submittedAt,long resetGeneration,boolean retryAllowed,boolean canRestore,String recoveryReason,TapSignals.Summary signals,
        boolean recordAccepted,boolean letterEarned,Integer rank,Restoration recovery) { }
    private static final Set<String> FAILED=Set.of("INVALID","EXPIRED","INTERRUPTED","REVIEW_REQUIRED");
    private static final String STATE="""
        CASE WHEN a.status='COUNTDOWN' THEN
          CASE WHEN a.submit_deadline<UTC_TIMESTAMP(6) THEN 'EXPIRED'
            WHEN a.ends_at<=UTC_TIMESTAMP(6) THEN 'AWAITING_SUBMISSION'
            WHEN a.starts_at<=UTC_TIMESTAMP(6) THEN 'PLAYING' ELSE 'COUNTDOWN' END
          ELSE a.status END
        """;
    private static final String SOURCE="""
        FROM tap_slots s JOIN tap_attempts a ON a.id=s.current_attempt_id AND a.user_id=s.user_id
        JOIN users u ON u.id=s.user_id LEFT JOIN profiles p ON p.user_id=u.id
        LEFT JOIN tap_recoveries recovery ON recovery.attempt_id=a.id AND recovery.user_id=a.user_id
        LEFT JOIN local_admin_accounts restore_actor ON restore_actor.user_id=recovery.actor_id
        WHERE a.archived_at IS NULL AND u.status='ACTIVE' AND u.role='USER'
          AND NOT EXISTS(SELECT 1 FROM local_admin_accounts la WHERE la.user_id=u.id)
        """;
    private final JdbcTemplate db;
    private final OperationsService operations;
    private final AuthRepository accounts;
    private final ParticipationRepository participation;
    private final MemberControlService controls;
    private final ResultsRepository results;
    private final ObjectMapper json;
    public AdminTapSupportService(JdbcTemplate db,OperationsService operations,AuthRepository accounts,
        ParticipationRepository participation,MemberControlService controls,ResultsRepository results,ObjectMapper json) {
        this.db=db;this.operations=operations;this.accounts=accounts;this.participation=participation;this.controls=controls;this.results=results;this.json=json;
    }
    private static AuthException error(int status,String code,String message){return new AuthException(status,code,message);}
    private static Instant time(ResultSet row,String column)throws SQLException{var ts=row.getTimestamp(column);return ts==null?null:ts.toInstant();}
    private static Restoration recovery(ResultSet row)throws SQLException {
        if(row.getTimestamp("restored_at")==null)return null;
        return new Restoration(row.getString("original_status"),row.getString("original_reason"),time(row,"original_deadline"),time(row,"effective_deadline"),
            time(row,"recognized_at"),time(row,"restored_at"),row.getLong("restore_actor_id"),row.getString("restore_operator_id"),row.getString("restore_reason"));
    }
    @Transactional
    public AdminAnalytics.Page<Attempt> list(AuthPrincipal actor,int offset,int limit,String filter,String search) {
        operations.access(actor);
        if(offset<0||offset>100000||limit<1||limit>50||!Set.of("FAILED","ALL","RECOVERED").contains(filter)||search!=null&&search.length()>64)
            throw error(400,"INVALID_TAP_FILTER","목록 범위와 검색 조건을 확인해 주세요.");
        var args=new ArrayList<Object>();String condition=SOURCE;
        if("FAILED".equals(filter))condition+=" AND ("+STATE+") IN ('INVALID','EXPIRED','INTERRUPTED','REVIEW_REQUIRED')";
        if("RECOVERED".equals(filter))condition+=" AND recovery.attempt_id IS NOT NULL";
        if(search!=null&&!search.isBlank()) {
            String q="%"+search.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            condition+=" AND (u.mattermost_username LIKE ? ESCAPE '!' OR u.real_name LIKE ? ESCAPE '!' OR p.nickname LIKE ? ESCAPE '!')";
            args.addAll(List.of(q,q,q));
        }
        long total=db.queryForObject("SELECT COUNT(*) "+condition,Long.class,args.toArray());
        args.add(limit);args.add(offset);
        var rows=db.query("""
            SELECT u.id user_id,u.mattermost_username,p.nickname,u.real_name,u.region,u.class_no,
              a.id attempt_id,a.review_reason,a.tap_count,a.payload_hash,a.starts_at,a.submit_deadline,a.submitted_at,
              recovery.original_status,recovery.original_reason,recovery.original_deadline,recovery.effective_deadline,
              recovery.recognized_at,recovery.restored_at,recovery.actor_id restore_actor_id,
              restore_actor.login_id restore_operator_id,recovery.reason restore_reason,
              (SELECT COALESCE(MAX(generation),0) FROM game_participation_resets g WHERE g.user_id=u.id) reset_generation,
              EXISTS(SELECT 1 FROM mission_progress mp JOIN missions m ON m.id=mp.mission_id
                WHERE mp.user_id=u.id AND m.code='FAST_TAP' AND mp.status='COMPLETED') completed,
            """+STATE+" effective_status "+condition+" ORDER BY a.id DESC LIMIT ? OFFSET ?",(r,n)->new Attempt(
                r.getLong("user_id"),r.getString("mattermost_username"),r.getString("nickname"),r.getString("real_name"),r.getString("region"),r.getObject("class_no",Integer.class),
                r.getLong("attempt_id"),r.getString("effective_status"),r.getString("review_reason"),r.getObject("tap_count",Integer.class),time(r,"starts_at"),time(r,"submit_deadline"),time(r,"submitted_at"),
                r.getLong("reset_generation"),FAILED.contains(r.getString("effective_status"))&&!r.getBoolean("completed"),false,null,null,
                "VALID".equals(r.getString("effective_status"))&&r.getObject("tap_count")!=null&&r.getString("payload_hash")!=null,
                r.getBoolean("completed"),null,recovery(r)),args.toArray());
        // Bounded page of stored events; no raw event arrays enter the admin DTO.
        var events=new HashMap<Long,List<Integer>>();
        var interruptions=new HashMap<Long,List<TapValidation.Interruption>>();
        if(!rows.isEmpty()) {
            String markers=String.join(",",Collections.nCopies(rows.size(),"?"));
            db.query("SELECT attempt_id,relative_time_ms,event_type FROM tap_attempt_events WHERE attempt_id IN ("+markers+") ORDER BY attempt_id,id",
                r->{if("TAP".equals(r.getString(3)))events.computeIfAbsent(r.getLong(1),ignored->new ArrayList<>()).add(r.getInt(2));
                    else interruptions.computeIfAbsent(r.getLong(1),ignored->new ArrayList<>()).add(new TapValidation.Interruption(r.getString(3),r.getInt(2)));},rows.stream().map(Attempt::attemptId).toArray());
        }
        var originals=new HashMap<Long,ResultsRepository.Attempt>();for(var original:results.attempts(rows.stream().map(Attempt::attemptId).toList()))originals.put(original.id(),original);
        var ranks=new HashMap<Long,Integer>();
        if(!rows.isEmpty())db.query("SELECT id,rank_no FROM ("+TapRankingService.rankedSql()+") ranked WHERE id IN ("+String.join(",",Collections.nCopies(rows.size(),"?"))+")",
            r->{ranks.put(r.getLong(1),r.getInt(2));},rows.stream().map(Attempt::attemptId).toArray());
        var items=rows.stream().map(a->{var taps=events.getOrDefault(a.attemptId(),List.of());
            var decision=TapRecoveryRules.inspect(originals.get(a.attemptId()),new ResultsRepository.StoredEvents(taps,interruptions.getOrDefault(a.attemptId(),List.of())),json);
            boolean restore=a.retryAllowed()&&decision.recoverable();
            return new Attempt(a.userId(),a.username(),a.nickname(),a.realName(),a.region(),a.classNo(),a.attemptId(),a.status(),a.reason(),a.tapCount(),a.startsAt(),a.submitDeadline(),a.submittedAt(),
                a.resetGeneration(),a.retryAllowed()&&!restore,restore,decision.reason(),TapSignals.inspect(taps),a.recordAccepted(),a.letterEarned(),ranks.get(a.attemptId()),a.recovery());}).toList();
        return new AdminAnalytics.Page<>(items,total,offset,offset+items.size()<total?offset+items.size():null);
    }
    @Transactional
    public ParticipationResetService.ResetRecord retry(AuthPrincipal actor,long user,String key,RetryRequest input) {
        if(user<1||key==null||!key.matches("[A-Za-z0-9_-]{16,64}")||input.attemptId()<1||input.expectedGeneration()<0||input.reason()==null||input.reason().isBlank()||input.reason().length()>300||!"RETRY".equals(input.confirmation()))
            throw error(400,"INVALID_TAP_RETRY","대상 도전과 재도전 사유를 확인해 주세요.");
        // Match the existing admin/user lock order before checking the current attempt.
        java.util.stream.LongStream.of(actor.userId(),user).distinct().sorted().forEach(accounts::lockUser);
        operations.access(actor);
        var change=new MemberControlService.ChangeRequest(input.expectedGeneration(),input.reason(),"APPLY","REVOKE");
        var replay=db.queryForList("""
            SELECT user_id,operation,mission_code,
              CAST(JSON_UNQUOTE(JSON_EXTRACT(snapshot_json,'$.tapSlots[0].current_attempt_id')) AS UNSIGNED) attempt_id
            FROM game_participation_resets WHERE actor_id=? AND request_key=?
            """,actor.userId(),key);
        if(!replay.isEmpty()) {
            var old=replay.getFirst();
            if(((Number)old.get("user_id")).longValue()!=user||!"REVOKE".equals(old.get("operation"))||!"FAST_TAP".equals(old.get("mission_code"))
                ||!(old.get("attempt_id") instanceof Number id)||id.longValue()!=input.attemptId())
                throw error(409,"IDEMPOTENCY_CONFLICT","같은 요청의 대상 도전이 바뀌었어요.");
            return controls.change(actor,user,"FAST_TAP",key,change);
        }
        participation.requireGeneration(user,input.expectedGeneration());
        var states=db.query("SELECT a.id,"+STATE+" state "+SOURCE+" AND u.id=?",(r,n)->Map.entry(r.getLong(1),r.getString(2)),user);
        if(states.isEmpty()||states.getFirst().getKey()!=input.attemptId())throw error(409,"TAP_ATTEMPT_CHANGED","현재 도전이 바뀌었어요. 목록을 다시 확인해 주세요.");
        if(!FAILED.contains(states.getFirst().getValue())||"COMPLETED".equals(participation.mission(user,MissionCode.FAST_TAP).state()))
            throw error(409,"TAP_RETRY_NOT_ALLOWED","진행 중이거나 정상 완료한 도전은 재도전 대상이 아니에요.");
        var current=results.current(user);
        if(TapRecoveryRules.inspect(current,results.events(current.id()),json).recoverable())throw error(409,"TAP_RECOVERY_AVAILABLE","기존 기록을 복구할 수 있어요. 재도전보다 원본 복구를 먼저 진행해 주세요.");
        return controls.change(actor,user,"FAST_TAP",key,change);
    }
}
