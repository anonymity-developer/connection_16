package com.tagme.content;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import static com.tagme.content.ContentModels.*;

@Repository
public class ContentRepository {
    private final JdbcTemplate db;
    public ContentRepository(JdbcTemplate db) { this.db=db; }
    public Instant now() { return db.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant(); }
    private Instant instant(ResultSet r,String name) throws SQLException { Timestamp t=r.getTimestamp(name);return t==null?null:t.toInstant(); }
    private Item mapItem(ResultSet r,int n) throws SQLException { return new Item(r.getLong("id"),r.getLong("author_id"),Kind.valueOf(r.getString("kind")),r.getString("visibility_status"),r.getObject("approved_revision_id",Long.class),r.getObject("pending_revision_id",Long.class),r.getObject("luck_score",Integer.class),r.getObject("landing_zone",Integer.class),r.getLong("version")); }
    private Revision mapRevision(ResultSet r,int n) throws SQLException { return new Revision(r.getLong("id"),r.getLong("item_id"),r.getInt("revision_no"),r.getString("content_text"),r.getString("template_id"),r.getString("style"),r.getBoolean("anonymous"),instant(r,"submitted_at"),r.getString("moderation_status"),r.getString("reason_code"),r.getString("payload_hash")); }
    public Item own(long user,Kind kind) { return db.query("SELECT * FROM content_items WHERE author_id=? AND slot_kind=?",this::mapItem,user,kind.name()).stream().findFirst().orElse(null); }
    public Item item(long id,boolean lock) { return db.query("SELECT * FROM content_items WHERE id=?"+(lock?" FOR UPDATE":""),this::mapItem,id).stream().findFirst().orElse(null); }
    public Revision revision(long id) { return db.query("SELECT * FROM content_revisions WHERE id=?",this::mapRevision,id).stream().findFirst().orElse(null); }
    public Revision latest(long id) { return db.query("SELECT * FROM content_revisions WHERE item_id=? ORDER BY revision_no DESC LIMIT 1",this::mapRevision,id).stream().findFirst().orElse(null); }
    public Revision byKey(long id,String key) { return db.query("SELECT * FROM content_revisions WHERE item_id=? AND idempotency_key=?",this::mapRevision,id,key).stream().findFirst().orElse(null); }
    private long insert(String sql,Object... values) {
        var key=new GeneratedKeyHolder();db.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;},key);return Objects.requireNonNull(key.getKey()).longValue();
    }
    public long create(long user,Kind kind,Integer luck,Integer zone) { return insert("INSERT INTO content_items(author_id,kind,luck_score,landing_zone) VALUES (?,?,?,?)",user,kind.name(),luck,zone); }
    public int[] luckPolicy() { return db.query("SELECT * FROM content_settings WHERE id=1 FOR SHARE",(r,n)->new int[]{r.getInt("wish_luck_min"),r.getInt("wish_luck_max"),r.getInt("wish_zone_count")}).stream().findFirst().orElse(null); }
    public long addRevision(long item,int number,ContentController.Submit body,String key,String hash,Instant received) {
        return insert("INSERT INTO content_revisions(item_id,revision_no,content_text,template_id,style,anonymous,submitted_at,idempotency_key,payload_hash) VALUES (?,?,?,?,?,?,?,?,?)",item,number,body.content().strip(),body.templateId(),body.style(),body.anonymous(),Timestamp.from(received),key,hash);
    }
    public void queue(Item item,long revision) {
        if(item.pendingId()!=null) {
            db.update("UPDATE moderation_jobs SET status='SUPERSEDED',fence=fence+1,lease_until=NULL WHERE revision_id=?",item.pendingId());
            db.update("UPDATE content_revisions SET moderation_status='SUPERSEDED',reason_code='NEW_REVISION' WHERE id=?",item.pendingId());
        }
        db.update("UPDATE content_items SET pending_revision_id=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",revision,item.id());
        db.update("INSERT INTO moderation_jobs(revision_id) VALUES (?)",revision);
    }
    private static final String JOBS="SELECT j.*,r.item_id,i.author_id FROM moderation_jobs j JOIN content_revisions r ON r.id=j.revision_id JOIN content_items i ON i.id=r.item_id";
    private Job mapJob(ResultSet r,int n) throws SQLException { return new Job(r.getLong("id"),r.getLong("revision_id"),r.getLong("author_id"),r.getLong("item_id"),r.getString("status"),r.getInt("attempts"),r.getLong("fence"),instant(r,"lease_until")); }
    public Job job(long id,boolean lock) { return db.query(JOBS+" WHERE j.id=?"+(lock?" FOR UPDATE":""),this::mapJob,id).stream().findFirst().orElse(null); }
    public List<Job> reviewQueue() { return db.query(JOBS+" WHERE j.status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED') ORDER BY j.id LIMIT 50",this::mapJob); }
    public Work claim() {
        var ids=db.queryForList("SELECT id FROM moderation_jobs WHERE (status='QUEUED' AND next_run_at<=UTC_TIMESTAMP(6)) OR (status='PROCESSING' AND lease_until<UTC_TIMESTAMP(6)) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",Long.class);
        if(ids.isEmpty())return null;
        long id=ids.getFirst();db.update("UPDATE moderation_jobs SET status='PROCESSING',attempts=attempts+1,fence=fence+1,lease_until=UTC_TIMESTAMP(6)+INTERVAL 30 SECOND WHERE id=?",id);
        var job=job(id,false);db.update("UPDATE content_revisions SET moderation_status='PROCESSING' WHERE id=?",job.revisionId());return new Work(job,revision(job.revisionId()).content());
    }
    public void retry(Job j,String code) { db.update("UPDATE moderation_jobs SET status='QUEUED',lease_until=NULL,last_error_code=?,next_run_at=UTC_TIMESTAMP(6)+INTERVAL ? SECOND WHERE id=?",code,Math.min(120,5*(1<<Math.min(j.attempts(),4))),j.id()); db.update("UPDATE content_revisions SET moderation_status='QUEUED' WHERE id=?",j.revisionId()); }
    public void decision(Job j,String decision,String reason,String detail,String provider,String policy,Long reviewer) {
        db.update("UPDATE moderation_jobs SET status=?,lease_until=NULL,last_error_code=?,provider=?,policy_version=? WHERE id=?",decision,reason,provider,policy,j.id());
        db.update("UPDATE content_revisions SET moderation_status=?,reason_code=? WHERE id=?",decision,reason,j.revisionId());
        db.update("INSERT INTO moderation_reviews(job_id,fence,reviewer_id,decision,reason) VALUES (?,?,?,?,?)",j.id(),j.fence(),reviewer,decision,detail);
        if("APPROVED".equals(decision))db.update("UPDATE content_items SET approved_revision_id=?,pending_revision_id=NULL,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",j.revisionId(),j.itemId());
        else if("REJECTED".equals(decision)) {
            db.update("UPDATE content_items SET pending_revision_id=NULL,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",j.itemId());
            db.update("UPDATE mission_progress p JOIN missions m ON m.id=p.mission_id JOIN content_items i ON i.author_id=p.user_id SET p.status='UNLOCKED' WHERE i.id=? AND i.kind IN ('WISH','CHEER') AND m.code=IF(i.kind='WISH','MAKE_A_WISH','SAY_SOMETHING') AND p.status='IN_PROGRESS'",j.itemId());
        }
    }
    public Job fenceManual(Job j) { db.update("UPDATE moderation_jobs SET fence=fence+1 WHERE id=?",j.id());return job(j.id(),false); }
    public Instant moderationClose() { return db.queryForObject("SELECT moderation_closes_at FROM event_settings WHERE id=1 FOR SHARE",Timestamp.class).toInstant(); }
    public boolean inModerationWindow() { return Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*)=1 FROM event_settings WHERE id=1 AND UTC_TIMESTAMP(6)>=opens_at AND UTC_TIMESTAMP(6)<=moderation_closes_at",Boolean.class)); }
    public Feed feed(Kind kind,long cursor) {
        var rows=db.query("""
            SELECT i.id,r.content_text,r.template_id,r.style,r.anonymous,p.nickname,i.luck_score,i.landing_zone
            FROM content_items i JOIN content_revisions r ON r.id=i.approved_revision_id
            JOIN users u ON u.id=i.author_id JOIN profiles p ON p.user_id=u.id
            WHERE i.kind=? AND i.id<? AND i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED'
              AND u.status='ACTIVE' AND p.is_public=TRUE
              AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
            ORDER BY i.id DESC LIMIT 21
            """,(r,n)->new PublicContent(r.getLong("id"),r.getString("content_text"),r.getString("template_id"),r.getString("style"),r.getBoolean("anonymous"),r.getBoolean("anonymous")?null:r.getString("nickname"),r.getObject("luck_score",Integer.class),r.getObject("landing_zone",Integer.class)),kind.name(),cursor);
        boolean more=rows.size()>20;var items=rows.stream().limit(20).toList();return new Feed(items,more?items.getLast().id():null);
    }
    public void markInProgress(long user,String mission) { db.update("UPDATE mission_progress p JOIN missions m ON m.id=p.mission_id SET p.status='IN_PROGRESS' WHERE p.user_id=? AND m.code=? AND p.status='UNLOCKED'",user,mission); }
    public long receiptId(long user,String kind,long revision) { return db.queryForObject("SELECT id FROM submission_receipts WHERE user_id=? AND source_kind=? AND source_id=?",Long.class,user,kind,revision); }
    public void hide(long item,String visibility) { db.update("UPDATE content_items SET visibility_status=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",visibility,item); }
}
