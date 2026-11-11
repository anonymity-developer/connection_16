package com.tagme.content;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import static com.tagme.content.ContentModels.*;

@Repository
public class ReactionRepository {
    public record CommentLink(long id,long wishId,long itemId,long authorId,long wishAuthorId,String hash) { }
    public record PublicComment(long id,String content,boolean revealProfile,String nickname,Long profileId) { }
    public record OwnComment(long id,String content,boolean revealProfile,String status,String reason,java.time.Instant submittedAt,String requestKey) { }
    public record Comments(List<PublicComment> items,Long nextCursor) { }
    public record OwnComments(List<OwnComment> items,Long nextCursor) { }
    public record LikeState(boolean liked,long count,boolean canLike) { }
    private final JdbcTemplate db;
    public ReactionRepository(JdbcTemplate db) { this.db=db; }
    // Used by reads and, after locking the author(s), by writes and approval.
    public boolean visible(long id,Kind kind) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT COUNT(*)=1 FROM content_items i JOIN content_revisions r ON r.id=i.approved_revision_id
            JOIN users u ON u.id=i.author_id JOIN profiles p ON p.user_id=u.id
            WHERE i.id=? AND i.kind=? AND i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED'
            AND u.status='ACTIVE' AND p.is_public=TRUE AND
            2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
            """,Boolean.class,id,kind.name()));
    }
    private static final String LINKS="SELECT c.*,i.author_id AS wish_author_id FROM wish_comments c JOIN content_items i ON i.id=c.wish_id";
    private CommentLink mapLink(ResultSet r,int n) throws SQLException { return new CommentLink(r.getLong("id"),r.getLong("wish_id"),r.getLong("content_item_id"),r.getLong("author_id"),r.getLong("wish_author_id"),r.getString("payload_hash")); }
    public CommentLink byItem(long itemId) { return db.query(LINKS+" WHERE c.content_item_id=?",this::mapLink,itemId).stream().findFirst().orElse(null); }
    public CommentLink byKey(long author,String key) { return db.query(LINKS+" WHERE c.author_id=? AND c.idempotency_key=?",this::mapLink,author,key).stream().findFirst().orElse(null); }
    public long saveComment(long wish,long item,long author,String key,String hash) {
        var keys=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement("INSERT INTO wish_comments(wish_id,content_item_id,author_id,idempotency_key,payload_hash) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,wish);s.setLong(2,item);s.setLong(3,author);s.setString(4,key);s.setString(5,hash);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();
    }
    public Comments comments(long wish,long cursor) {
        var rows=db.query("""
            SELECT c.id,c.author_id,r.content_text,r.anonymous,p.nickname FROM wish_comments c
            JOIN content_items i ON i.id=c.content_item_id JOIN content_revisions r ON r.id=i.approved_revision_id
            JOIN users u ON u.id=c.author_id JOIN profiles p ON p.user_id=u.id
            JOIN content_items w ON w.id=c.wish_id JOIN content_revisions wr ON wr.id=w.approved_revision_id
            JOIN users wu ON wu.id=w.author_id JOIN profiles wp ON wp.user_id=wu.id
            WHERE c.wish_id=? AND c.id<? AND w.kind='WISH' AND w.visibility_status='VISIBLE' AND wr.moderation_status='APPROVED'
            AND wu.status='ACTIVE' AND wp.is_public=TRUE AND
            2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=wu.id AND uc.withdrawn_at IS NULL)
            AND i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED'
            AND u.status='ACTIVE' AND p.is_public=TRUE AND
            2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
            ORDER BY c.id DESC LIMIT 21
            """,(r,n)->new PublicComment(r.getLong("id"),r.getString("content_text"),!r.getBoolean("anonymous"),r.getBoolean("anonymous")?null:r.getString("nickname"),r.getBoolean("anonymous")?null:r.getLong("author_id")),wish,cursor);
        var items=rows.stream().limit(20).toList();return new Comments(items,rows.size()>20?items.getLast().id():null);
    }
    public OwnComments own(long wish,long user,long cursor) {
        var rows=db.query("""
            SELECT c.id AS comment_id,c.idempotency_key AS request_key,r.* FROM wish_comments c JOIN content_items active ON active.id=c.content_item_id AND active.archived_at IS NULL JOIN content_revisions r ON r.item_id=c.content_item_id
            WHERE c.wish_id=? AND c.author_id=? AND c.id<? ORDER BY c.id DESC LIMIT 21
            """,(r,n)->new OwnComment(r.getLong("comment_id"),r.getString("content_text"),!r.getBoolean("anonymous"),r.getString("moderation_status"),r.getString("reason_code"),r.getTimestamp("submitted_at").toInstant(),r.getString("request_key")),wish,user,cursor);
        var items=rows.stream().limit(20).toList();return new OwnComments(items,rows.size()>20?items.getLast().id():null);
    }
    public long count(long card) {
        return db.queryForObject("""
            SELECT COUNT(*) FROM cheer_likes l JOIN users u ON u.id=l.user_id JOIN profiles p ON p.user_id=u.id
            WHERE l.card_id=? AND u.status='ACTIVE' AND p.is_public=TRUE AND
            2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
            """,Long.class,card);
    }
    public boolean liked(long card,long user) { return db.queryForObject("SELECT COUNT(*) FROM cheer_likes WHERE card_id=? AND user_id=?",Integer.class,card,user)==1; }
    public Map<Long,LikeState> states(List<Long> ids,long user,boolean allowed) {
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        List<Object> args=new ArrayList<>();args.add(user);args.addAll(ids);
        var rows=db.query("""
            SELECT i.id,i.author_id,
            EXISTS(SELECT 1 FROM cheer_likes mine WHERE mine.card_id=i.id AND mine.user_id=?) AS liked,
            (SELECT COUNT(*) FROM cheer_likes l JOIN users lu ON lu.id=l.user_id JOIN profiles lp ON lp.user_id=lu.id
             WHERE l.card_id=i.id AND lu.status='ACTIVE' AND lp.is_public=TRUE AND
             2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=lu.id AND uc.withdrawn_at IS NULL)) AS like_count
            FROM content_items i JOIN content_revisions r ON r.id=i.approved_revision_id
            JOIN users u ON u.id=i.author_id JOIN profiles p ON p.user_id=u.id
            WHERE i.kind='CHEER' AND i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED'
            AND u.status='ACTIVE' AND p.is_public=TRUE AND
            2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
            AND i.id IN (
            """+placeholders+")",(r,n)->Map.entry(r.getLong("id"),new LikeState(r.getBoolean("liked"),r.getLong("like_count"),allowed&&r.getLong("author_id")!=user)),args.toArray());
        Map<Long,LikeState> result=new LinkedHashMap<>();for(var row:rows)result.put(row.getKey(),row.getValue());return result;
    }
    public void like(long card,long user) { db.update("INSERT INTO cheer_likes(card_id,user_id) VALUES (?,?) ON DUPLICATE KEY UPDATE card_id=VALUES(card_id)",card,user); }
    public void unlike(long card,long user) { db.update("DELETE FROM cheer_likes WHERE card_id=? AND user_id=?",card,user); }
}
