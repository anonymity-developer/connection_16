package com.tagme.results;

import com.tagme.auth.*;
import com.tagme.participation.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.tagme.participation.ParticipationModels.MissionCode;

@Service
public class TapRankingService {
    public record Entry(int rank,String nickname,int tapCount,Instant submittedAt,String message) { }
    public record Own(long attemptId,Integer rank,int tapCount,boolean recordBreaker,boolean canWriteMessage,String message) { }
    public record Board(List<Entry> top,Own own) { }
    private final JdbcTemplate db;
    private final ParticipationRepository participation;
    private final AuthRateLimiter limits;
    private final TransactionTemplate tx;
    public TapRankingService(JdbcTemplate db,ParticipationRepository participation,AuthRateLimiter limits,PlatformTransactionManager manager) {
        this.db=db;this.participation=participation;this.limits=limits;tx=new TransactionTemplate(manager);
    }
    private AuthException error(int status,String code,String text){return new AuthException(status,code,text);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockForReset(){db.queryForObject("SELECT id FROM tap_record_lock WHERE id=1 FOR UPDATE",Integer.class);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void complete(long user,long attempt,int count,Instant received,Runnable save) {
        lockForReset();
        // Locking read sees the latest committed score even if the caller already has a snapshot.
        // Do not lock other users/profiles here: those follow the per-user lock ordering.
        var best=db.queryForList("SELECT tap_count FROM tap_attempts WHERE status='VALID' AND archived_at IS NULL AND tap_count IS NOT NULL ORDER BY tap_count DESC,submitted_at,id LIMIT 1 FOR SHARE",Integer.class);
        Integer previous=best.isEmpty()?null:best.getFirst();
        save.run();
        if(previous==null||count>previous)
            db.update("INSERT INTO tap_record_achievements(attempt_id,user_id,previous_best,achieved_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE attempt_id=tap_record_achievements.attempt_id",attempt,user,previous,Timestamp.from(received));
    }
    private void reader(AuthPrincipal actor) {
        participation.lockPrincipal(actor);
        var mission=participation.mission(actor.userId(),MissionCode.FAST_TAP);participation.activeMission(mission.id());
        if("LOCKED".equals(mission.state()))throw error(403,"MISSION_LOCKED","먼저 현장 태그로 미션을 열어주세요.");
        if(!participation.eligible(actor.userId()))throw error(409,"PROFILE_REQUIRED","프로필과 현재 공개 동의를 확인해 주세요.");
    }
    private static final String RANKED="""
      SELECT ROW_NUMBER() OVER(ORDER BY a.tap_count DESC,a.submitted_at,a.id) AS rank_no,
        a.id,a.user_id,a.tap_count,a.submitted_at,p.nickname,c.message_text
      FROM tap_attempts a JOIN tap_slots s ON s.current_attempt_id=a.id AND s.user_id=a.user_id
      JOIN users u ON u.id=a.user_id JOIN profiles p ON p.user_id=u.id
      JOIN mission_progress mp ON mp.user_id=u.id JOIN missions m ON m.id=mp.mission_id AND m.code='FAST_TAP'
      LEFT JOIN tap_record_achievements c ON c.attempt_id=a.id
      WHERE a.status='VALID' AND a.archived_at IS NULL AND mp.status='COMPLETED' AND u.status='ACTIVE'
        AND p.is_public=TRUE AND TRIM(p.nickname)<>'' AND u.mattermost_profile_synced_at IS NOT NULL
        AND TRIM(COALESCE(u.real_name,''))<>'' AND TRIM(COALESCE(u.region,''))<>'' AND u.class_no>0
        AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc
          ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
        AND EXISTS(SELECT 1 FROM mission_completion_evidence e WHERE e.user_id=u.id AND e.mission_id=m.id
          AND e.source_kind='GAME_ATTEMPT' AND e.source_id=a.id)
      """;
    public Board board(AuthPrincipal actor) {
        return tx.execute(s->{
            reader(actor);
            var top=db.query("SELECT * FROM ("+RANKED+") ranked WHERE rank_no<=10 ORDER BY rank_no",
                (r,n)->new Entry(r.getInt("rank_no"),r.getString("nickname"),r.getInt("tap_count"),r.getTimestamp("submitted_at").toInstant(),r.getString("message_text")));
            var ranks=db.queryForList("SELECT rank_no FROM ("+RANKED+") ranked WHERE user_id=?",Integer.class,actor.userId());
            boolean open="OPEN".equals(participation.event(false).status());
            var own=db.query("SELECT a.id,a.tap_count,c.attempt_id AS record_id,c.message_text FROM tap_slots s JOIN tap_attempts a ON a.id=s.current_attempt_id AND a.user_id=s.user_id LEFT JOIN tap_record_achievements c ON c.attempt_id=a.id WHERE s.user_id=? AND a.archived_at IS NULL AND a.status='VALID'",
                (r,n)->new Own(r.getLong("id"),ranks.isEmpty()?null:ranks.getFirst(),r.getInt("tap_count"),r.getObject("record_id")!=null,open&&r.getObject("record_id")!=null&&r.getString("message_text")==null,r.getString("message_text")),actor.userId());
            return new Board(top,own.isEmpty()?null:own.getFirst());
        });
    }
    public Board message(AuthPrincipal actor,long attempt,String text,String key,long generation) {
        if(text==null||text.isBlank()||text.length()>100||key==null||!key.matches("[A-Za-z0-9_-]{16,64}"))
            throw error(400,"INVALID_RECORD_MESSAGE","소감을 100자 이내로 입력해 주세요.");
        limits.check("tap-message",Long.toString(actor.userId()),10,60);
        return tx.execute(s->{
            reader(actor);participation.requireGeneration(actor.userId(),generation);
            var rows=db.queryForList("SELECT c.message_text,c.message_key FROM tap_record_achievements c JOIN tap_attempts a ON a.id=c.attempt_id JOIN tap_slots slot ON slot.current_attempt_id=a.id AND slot.user_id=a.user_id WHERE c.attempt_id=? AND c.user_id=? AND a.archived_at IS NULL AND a.status='VALID'",attempt,actor.userId());
            if(rows.isEmpty())throw error(403,"RECORD_MESSAGE_NOT_ALLOWED","최고 기록을 갱신한 도전에서만 소감을 남길 수 있어요.");
            var saved=rows.getFirst();String stripped=text.strip();
            if(saved.get("message_text")!=null) {
                if(!key.equals(saved.get("message_key"))||!stripped.equals(saved.get("message_text")))throw error(409,"RECORD_MESSAGE_SAVED","이미 소감을 남겼어요. 소감은 한 번만 작성할 수 있어요.");
                return board(actor);
            }
            participation.requireOpen(false);
            if(db.queryForObject("SELECT COUNT(*) FROM tap_record_achievements WHERE user_id=? AND message_key=?",Integer.class,actor.userId(),key)>0)
                throw error(409,"IDEMPOTENCY_CONFLICT","같은 요청 식별자가 다른 소감에 사용됐어요.");
            db.update("UPDATE tap_record_achievements SET message_text=?,message_key=?,message_at=UTC_TIMESTAMP(6) WHERE attempt_id=?",stripped,key,attempt);
            return board(actor);
        });
    }
}
