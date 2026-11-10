package com.tagme.operations;

import com.tagme.content.ContentModels.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationsRepository {
    private final JdbcTemplate db;
    public OperationsRepository(JdbcTemplate db) { this.db=db; }
    public List<Long> contentIds(long cursor,int limit,String kind,String visibility) {
        String sql="SELECT id FROM content_items WHERE id<?";List<Object> args=new ArrayList<>();args.add(cursor);
        if(kind!=null){sql+=" AND kind=?";args.add(kind);}if(visibility!=null){sql+=" AND visibility_status=?";args.add(visibility);}
        sql+=" ORDER BY id DESC LIMIT ?";args.add(limit);return db.queryForList(sql,Long.class,args.toArray());
    }
    public List<Long> taskIds(long after,int limit,String status) {
        String sql="SELECT id FROM moderation_jobs WHERE id>?";List<Object> args=new ArrayList<>();args.add(after);
        if("PENDING".equals(status))sql+=" AND status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')";
        else if(status!=null){sql+=" AND status=?";args.add(status);}
        sql+=" ORDER BY id LIMIT ?";args.add(limit);return db.queryForList(sql,Long.class,args.toArray());
    }
    public int retryCount(long job) { return db.queryForObject("SELECT manual_retry_count FROM moderation_jobs WHERE id=?",Integer.class,job); }
    public void retry(Job job) {
        db.update("UPDATE moderation_jobs SET status='QUEUED',attempts=0,manual_retry_count=manual_retry_count+1,fence=fence+1,lease_until=NULL,next_run_at=UTC_TIMESTAMP(6),provider=NULL,policy_version=NULL,last_error_code=NULL WHERE id=?",job.id());
        db.update("UPDATE content_revisions SET moderation_status='QUEUED',reason_code=NULL WHERE id=?",job.revisionId());
    }
    public void visibility(Item item,String next) {
        db.update("UPDATE content_items SET visibility_status=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",next,item.id());
        if("HIDDEN".equals(next)&&item.pendingId()!=null) {
            db.update("UPDATE moderation_jobs SET status='REVIEW_REQUIRED',fence=fence+1,lease_until=NULL,last_error_code='CONTENT_HIDDEN' WHERE revision_id=? AND status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')",item.pendingId());
            db.update("UPDATE content_revisions SET moderation_status='REVIEW_REQUIRED',reason_code='CONTENT_HIDDEN' WHERE id=?",item.pendingId());
        }
    }
    /* No bodies, MM identities, keys or raw answers enter the candidate response. These reads
       must run in the service's one REPEATABLE_READ transaction after current-role validation. */
    private static final String AWARD_BASE = """
        WITH public_users AS (
          SELECT u.id,p.nickname,p.published_at,p.version FROM users u JOIN profiles p ON p.user_id=u.id
          WHERE u.status='ACTIVE' AND p.is_public=TRUE
            AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc
              ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version
              WHERE uc.user_id=u.id AND uc.withdrawn_at IS NULL)
        ), eligible AS (
          SELECT p.* FROM public_users p JOIN users u ON u.id=p.id JOIN user_credentials credential ON credential.user_id=u.id
          WHERE p.published_at IS NOT NULL AND CHAR_LENGTH(TRIM(p.nickname))>0
            AND CHAR_LENGTH(TRIM(u.real_name))>0 AND CHAR_LENGTH(TRIM(u.region))>0 AND u.class_no>0 AND u.mattermost_profile_synced_at IS NOT NULL
        ), public_items AS (
          SELECT i.id,i.author_id,i.kind,i.approved_revision_id,r.submitted_at
          FROM content_items i JOIN content_revisions r ON r.id=i.approved_revision_id AND r.item_id=i.id
          JOIN moderation_jobs approved_job ON approved_job.revision_id=r.id AND approved_job.status='APPROVED'
          JOIN public_users u ON u.id=i.author_id JOIN event_settings ev ON ev.id=1
          WHERE i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED'
            AND r.submitted_at>=ev.opens_at AND r.submitted_at<ev.submit_closes_at AND r.submitted_at<=UTC_TIMESTAMP(6)
        )
        """;
    private static java.time.Instant at(java.sql.ResultSet row,String name) throws java.sql.SQLException {
        var value=row.getTimestamp(name);return value==null?null:value.toInstant();
    }
    private <T> List<T> queryAt(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,java.time.Instant observedAt) {
        int count=(sql.length()-sql.replace("UTC_TIMESTAMP(6)","").length())/"UTC_TIMESTAMP(6)".length();
        return db.query(sql.replace("UTC_TIMESTAMP(6)","?"),mapper,java.util.Collections.nCopies(count,java.sql.Timestamp.from(observedAt)).toArray());
    }
    public AwardModels.Facts awardFacts() { return awardFacts(db.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant()); }
    public AwardModels.Facts awardFacts(java.time.Instant observedAt) {
        var window=queryAt("SELECT UTC_TIMESTAMP(6) observed_at,opens_at,submit_closes_at,moderation_closes_at FROM event_settings WHERE id=1",
            (r,n)->new AwardModels.Window(at(r,"observed_at"),at(r,"opens_at"),at(r,"submit_closes_at"),at(r,"moderation_closes_at")),observedAt).stream().findFirst()
            .orElseGet(()->new AwardModels.Window(observedAt,null,null,null));
        var users=queryAt(AWARD_BASE+" SELECT id,nickname,published_at FROM eligible ORDER BY id",
            (r,n)->new AwardModels.User(r.getLong("id"),r.getString("nickname"),at(r,"published_at")),observedAt);
        var proofs=queryAt(AWARD_BASE+"""
          SELECT p.user_id,m.code,e.source_kind,e.source_id,e.receipt_order,e.accepted_received_at,
            (p.status='COMPLETED' AND p.completed_at IS NOT NULL AND p.completed_at<=ev.moderation_closes_at AND p.completed_at<=UTC_TIMESTAMP(6)
             AND e.accepted_received_at<=UTC_TIMESTAMP(6) AND e.accepted_received_at>=ev.opens_at AND e.accepted_received_at<ev.submit_closes_at
             AND e.accepted_received_at>=receipt.received_at AND p.completed_at>=e.accepted_received_at
             AND (m.code='SHOW_ME' OR (p.unlocked_at>=ev.opens_at AND receipt.received_at>=p.unlocked_at AND receipt.received_at>=ev.opens_at AND receipt.received_at<ev.submit_closes_at))
             AND CASE m.code
               WHEN 'SHOW_ME' THEN
                 (e.source_kind='PROFILE' AND e.source_id>0 AND e.source_id<=u.version)
                 OR (e.source_kind='TAG_UNLOCK' AND e.source_id=m.id AND p.unlocked_at IS NOT NULL)
               WHEN 'SYNC_ME' THEN e.source_kind='SYNC_RESULT' AND e.source_id=e.user_id AND sync.user_id IS NOT NULL
                 AND sync.submitted_at=receipt.received_at AND sync.submitted_at>=ev.opens_at AND sync.submitted_at<ev.submit_closes_at
               WHEN 'FAST_TAP' THEN e.source_kind='GAME_ATTEMPT' AND tap.id IS NOT NULL AND slot.current_attempt_id=tap.id
                 AND tap.status='VALID' AND CHAR_LENGTH(tap.payload_hash)=64 AND tap.review_reason IS NULL AND tap.tap_count>=0
                 AND tap.tap_count<=TIMESTAMPDIFF(MICROSECOND,tap.starts_at,tap.ends_at)/50000
                 AND tap.starts_at>=p.unlocked_at AND tap.submitted_at>=tap.ends_at AND tap.submitted_at<=tap.submit_deadline
                 AND tap.ends_at<ev.submit_closes_at AND tap.submitted_at=receipt.received_at
                 AND tap.tap_count=(SELECT COUNT(*) FROM tap_attempt_events t WHERE t.attempt_id=tap.id AND t.event_type='TAP')
                 AND NOT EXISTS (SELECT 1 FROM tap_attempt_events t WHERE t.attempt_id=tap.id AND (t.event_type<>'TAP' OR t.relative_time_ms<0 OR t.relative_time_ms>=TIMESTAMPDIFF(MICROSECOND,tap.starts_at,tap.ends_at)/1000))
               WHEN 'MAKE_A_WISH' THEN e.source_kind='WISH_REVISION' AND original.moderation_status='APPROVED' AND original_job.status='APPROVED'
                 AND original.submitted_at=receipt.received_at AND original.submitted_at>=ev.opens_at AND original.submitted_at<ev.submit_closes_at
                 AND item.author_id=e.user_id AND item.kind='WISH' AND live.id IS NOT NULL
               WHEN 'SAY_SOMETHING' THEN e.source_kind='CHEER_REVISION' AND original.moderation_status='APPROVED' AND original_job.status='APPROVED'
                 AND original.submitted_at=receipt.received_at AND original.submitted_at>=ev.opens_at AND original.submitted_at<ev.submit_closes_at
                 AND item.author_id=e.user_id AND item.kind='CHEER' AND live.id IS NOT NULL
               ELSE FALSE END) valid
          FROM mission_progress p JOIN eligible u ON u.id=p.user_id JOIN missions m ON m.id=p.mission_id
          LEFT JOIN mission_completion_evidence e ON e.user_id=p.user_id AND e.mission_id=m.id
          LEFT JOIN submission_receipts receipt ON receipt.id=e.receipt_order AND receipt.user_id=e.user_id
            AND receipt.source_kind=e.source_kind AND receipt.source_id=e.source_id
          JOIN event_settings ev ON ev.id=1
          LEFT JOIN sync_results sync ON m.code='SYNC_ME' AND sync.user_id=e.user_id
          LEFT JOIN tap_attempts tap ON m.code='FAST_TAP' AND tap.id=e.source_id AND tap.user_id=e.user_id
          LEFT JOIN tap_slots slot ON slot.user_id=e.user_id
          LEFT JOIN content_revisions original ON m.code IN ('MAKE_A_WISH','SAY_SOMETHING') AND original.id=e.source_id
          LEFT JOIN moderation_jobs original_job ON original_job.revision_id=original.id
          LEFT JOIN content_items item ON item.id=original.item_id
          LEFT JOIN public_items live ON live.id=item.id
          WHERE p.status='COMPLETED' ORDER BY p.user_id,m.code
          """,(r,n)->new AwardModels.Proof(r.getLong("user_id"),r.getString("code"),r.getString("source_kind"),r.getLong("source_id"),r.getLong("receipt_order"),at(r,"accepted_received_at"),r.getBoolean("valid")),observedAt);
        var comments=queryAt(AWARD_BASE+"""
          SELECT c.author_id,COUNT(DISTINCT c.wish_id) amount FROM wish_comments c
          JOIN public_items comment_item ON comment_item.id=c.content_item_id AND comment_item.kind='COMMENT' AND comment_item.author_id=c.author_id
          JOIN public_items wish ON wish.id=c.wish_id AND wish.kind='WISH' AND wish.author_id<>c.author_id
          JOIN submission_receipts receipt ON receipt.user_id=c.author_id AND receipt.source_kind='COMMENT_REVISION' AND receipt.source_id=comment_item.approved_revision_id
          JOIN event_settings ev ON ev.id=1
          WHERE receipt.received_at>=ev.opens_at AND receipt.received_at<ev.submit_closes_at GROUP BY c.author_id
          """,(r,n)->new AwardModels.Activity(r.getLong("author_id"),r.getLong("amount")),observedAt);
        var hearts=queryAt(AWARD_BASE+"""
          SELECT card.author_id,COUNT(DISTINCT heart.user_id) amount FROM cheer_likes heart
          JOIN public_items card ON card.id=heart.card_id AND card.kind='CHEER'
          JOIN public_users voter ON voter.id=heart.user_id AND voter.id<>card.author_id JOIN event_settings ev ON ev.id=1
          WHERE heart.created_at>=ev.opens_at AND heart.created_at<ev.submit_closes_at AND heart.created_at<=UTC_TIMESTAMP(6) GROUP BY card.author_id
          """,(r,n)->new AwardModels.Activity(r.getLong("author_id"),r.getLong("amount")),observedAt);
        var taps=queryAt(AWARD_BASE+"""
          SELECT tap.user_id,MAX(tap.tap_count) amount FROM tap_slots slot JOIN tap_attempts tap ON tap.id=slot.current_attempt_id AND tap.user_id=slot.user_id
          JOIN eligible u ON u.id=tap.user_id WHERE tap.status='VALID' AND tap.payload_hash IS NOT NULL AND tap.tap_count IS NOT NULL GROUP BY tap.user_id
          """,(r,n)->new AwardModels.Activity(r.getLong("user_id"),r.getLong("amount")),observedAt);
        var reviews=new ArrayList<>(queryAt("SELECT user_id,id,status FROM tap_attempts WHERE status='REVIEW_REQUIRED' ORDER BY id",
            (r,n)->new AwardModels.Review(r.getLong("user_id"),r.getLong("id"),"TAP_REVIEW_REQUIRED"),observedAt));
        reviews.addAll(queryAt(AWARD_BASE+" SELECT e.user_id FROM event_completions e LEFT JOIN eligible u ON u.id=e.user_id WHERE u.id IS NULL ORDER BY e.user_id",
            (r,n)->new AwardModels.Review(r.getLong("user_id"),0L,"INELIGIBLE_PROFILE_OR_CONSENT"),observedAt));
        long pending=db.queryForObject("SELECT COUNT(*) FROM moderation_jobs WHERE status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')",Long.class);
        return new AwardModels.Facts(window,users,proofs,comments,hearts,taps,reviews,pending);
    }

}
