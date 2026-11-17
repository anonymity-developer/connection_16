package com.tagme.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.participation.*;
import com.tagme.participation.ParticipationModels.MissionCode;
import com.tagme.results.*;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TapRecoveryService {
    public record Request(@Positive long attemptId,@PositiveOrZero long expectedGeneration,
        @NotBlank @Size(max=300) String reason,@NotNull @Pattern(regexp="RESTORE") String confirmation) { }
    public record Recovery(long attemptId,long userId,Instant recognizedAt,Instant restoredAt) { }
    private final JdbcTemplate db;private final AuthRepository accounts;private final OperationsService operations;
    private final ParticipationRepository participation;private final ResultsRepository results;private final ObjectMapper json;
    private final TapRankingService rankings;private final MissionCompletionService completion;private final AdminAuditRepository audit;
    public TapRecoveryService(JdbcTemplate db,AuthRepository accounts,OperationsService operations,ParticipationRepository participation,
        ResultsRepository results,ObjectMapper json,TapRankingService rankings,MissionCompletionService completion,AdminAuditRepository audit) {
        this.db=db;this.accounts=accounts;this.operations=operations;this.participation=participation;this.results=results;this.json=json;this.rankings=rankings;this.completion=completion;this.audit=audit;
    }
    private static AuthException error(int status,String code,String text){return new AuthException(status,code,text);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Recovery restore(AuthPrincipal actor,long user,String key,Request input) {
        if(user<1||key==null||!key.matches("[A-Za-z0-9_-]{16,64}")||input.attemptId()<1||input.expectedGeneration()<0||input.reason()==null||input.reason().isBlank()||input.reason().length()>300||!"RESTORE".equals(input.confirmation()))
            throw error(400,"INVALID_TAP_RECOVERY","복구할 기록과 사유를 확인해 주세요.");
        java.util.stream.LongStream.of(actor.userId(),user).distinct().sorted().forEach(accounts::lockUser);
        operations.access(actor);
        var replay=db.queryForList("SELECT * FROM tap_recoveries WHERE actor_id=? AND request_key=?",actor.userId(),key);
        if(!replay.isEmpty()) {
            var row=replay.getFirst();
            if(((Number)row.get("attempt_id")).longValue()!=input.attemptId()||((Number)row.get("user_id")).longValue()!=user||((Number)row.get("expected_generation")).longValue()!=input.expectedGeneration()||!input.reason().strip().equals(row.get("reason")))
                throw error(409,"IDEMPOTENCY_CONFLICT","같은 요청의 복구 대상이나 사유가 바뀌었어요.");
            return read(input.attemptId());
        }
        return apply(actor.userId(),user,key,input);
    }
    /** Internal worker entry point, never exposed as an unauthenticated HTTP operation. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Recovery restoreAutomatically(long user) {
        accounts.lockUser(user);
        var current=results.current(user);
        if(current==null||!TapRecoveryRules.inspect(current,results.events(current.id()),json).recoverable())return null;
        return apply(null,user,"auto-tap-recovery-"+current.id(),new Request(current.id(),participation.generation(user),
            "기존 FAST TAP 판정 기준 변경에 따른 원본 기록 자동 인정","RESTORE"));
    }
    private Recovery apply(Long actorId,long user,String key,Request input) {
        participation.requireGeneration(user,input.expectedGeneration());
        var account=accounts.byId(user).orElseThrow(AuthException::invalid);
        if(!"USER".equals(account.role())||!"ACTIVE".equals(account.status()))throw error(409,"MEMBER_INACTIVE","현재 참가 계정을 확인해 주세요.");
        var current=results.current(user);var decision=TapRecoveryRules.inspect(current,current==null?new ResultsRepository.StoredEvents(List.of(),List.of()):results.events(current.id()),json);
        if(current==null||current.id()!=input.attemptId()||!decision.recoverable())throw error(409,"TAP_NOT_RECOVERABLE","원본 기록으로 복구할 수 없어요. 목록을 확인하고 재도전을 지원해 주세요.");
        participation.requireOpen(false);var mission=participation.mission(user,MissionCode.FAST_TAP);participation.activeMission(mission.id());
        if("LOCKED".equals(mission.state())||"COMPLETED".equals(mission.state())||!participation.eligible(user)||current.startsAt().isBefore(mission.unlockedAt()))
            throw error(409,"TAP_RECOVERY_INELIGIBLE","현재 해금·프로필·획득 상태를 먼저 확인해 주세요.");
        var event=participation.event(true);
        if(current.submittedAt().isBefore(event.opensAt())||!current.submittedAt().isBefore(event.submitClosesAt()))throw error(409,"TAP_NOT_RECOVERABLE","행사 접수 기간 밖의 기록은 복구할 수 없어요.");
        rankings.complete(user,current.id(),current.count(),current.submittedAt(),()->{
            db.update("""
                INSERT INTO tap_recoveries(attempt_id,user_id,actor_id,request_key,expected_generation,reason,original_status,original_reason,original_deadline,effective_deadline,recognized_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,current.id(),user,actorId,key,input.expectedGeneration(),input.reason().strip(),current.status(),current.reason(),Timestamp.from(current.deadline()),Timestamp.from(current.endsAt().plusSeconds(60)),Timestamp.from(current.submittedAt()));
            results.reclassify(current.id(),new TapValidation.Verdict("VALID",null,current.count()));
            var existing=db.queryForList("SELECT id FROM submission_receipts WHERE user_id=? AND source_kind='GAME_ATTEMPT' AND source_id=?",Long.class,user,current.id());
            var receipt=existing.isEmpty()?participation.receipt(user,"GAME_ATTEMPT",current.id(),current.submittedAt()):participation.receipt(existing.getFirst());
            if(!receipt.receivedAt().equals(current.submittedAt()))throw error(409,"INVALID_RECOVERY_RECEIPT","원본 접수 근거가 일치하지 않아요.");
            completion.complete(user,MissionCode.FAST_TAP,receipt.id());
            db.update("UPDATE mission_progress SET completed_at=? WHERE user_id=? AND mission_id=?",Timestamp.from(current.submittedAt()),user,mission.id());
            if(actorId!=null)audit.record(actorId,"MISSION_ADJUSTED","USER",user,input.reason(),
                Map.of("operation","RESTORE_TAP","attemptId",current.id(),"status",current.status(),"reason",Objects.toString(current.reason(),""),"deadline",current.deadline().toString()),
                Map.of("status","VALID","attemptId",current.id(),"recognizedAt",current.submittedAt().toString()));
        });
        return read(current.id());
    }
    private Recovery read(long attempt) {
        return db.query("SELECT * FROM tap_recoveries WHERE attempt_id=?",(r,n)->new Recovery(r.getLong("attempt_id"),r.getLong("user_id"),r.getTimestamp("recognized_at").toInstant(),r.getTimestamp("restored_at").toInstant()),attempt).getFirst();
    }
}
