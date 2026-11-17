package com.tagme.operations;

import com.tagme.auth.AuthException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded keyset scan; each source is verified in its own locked transaction. */
@Component
@ConditionalOnProperty(name="tagme.tap.automatic-recovery-enabled",havingValue="true",matchIfMissing=true)
public class AutomaticTapRecovery {
    private static final Logger log=LoggerFactory.getLogger(AutomaticTapRecovery.class);
    private final JdbcTemplate db;
    private final TapRecoveryService recovery;
    private final AtomicBoolean running=new AtomicBoolean();
    private long cursor;
    public AutomaticTapRecovery(JdbcTemplate db,TapRecoveryService recovery){this.db=db;this.recovery=recovery;}
    @EventListener(ApplicationReadyEvent.class)
    public void ready(){runBatch();}
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void runBatch(){
        if(!running.compareAndSet(false,true))return;
        try {
            var users=db.queryForList("""
                SELECT u.id FROM users u JOIN tap_slots s ON s.user_id=u.id
                JOIN tap_attempts a ON a.id=s.current_attempt_id AND a.user_id=u.id
                WHERE a.submitted_at IS NOT NULL AND a.tap_count IS NOT NULL AND a.payload_hash IS NOT NULL AND u.id>? AND u.role='USER' AND u.status='ACTIVE' AND a.archived_at IS NULL
                  AND (a.status IN ('EXPIRED','REVIEW_REQUIRED') OR (a.status='INVALID' AND a.review_reason='DUPLICATE_MILLISECOND'))
                ORDER BY u.id LIMIT 100
                """,Long.class,cursor);
            int restored=0,skipped=0,failed=0;
            for(long user:users){
                cursor=user;
                try {if(recovery.restoreAutomatically(user)!=null)restored++;else skipped++;}
                catch(AuthException conflict){skipped++;}
                catch(RuntimeException failure){failed++;log.error("Automatic TAP recovery transaction failed",failure);}
            }
            if(users.size()<100)cursor=0;
            if(restored>0||failed>0)log.info("Automatic TAP recovery: restored={}, skipped={}, failed={}",restored,skipped,failed);
        } finally {running.set(false);}
    }
}
