package com.tagme.content;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.tagme.content.ContentModels.*;

/** Bounded keyset pages. Neither bodies nor identity/like eligibility are cached. */
@Service
public class CheerFeedService {
    public enum Sort { LATEST, HEARTS }
    public enum Direction { ASC, DESC }
    public record Page(List<PublicContent> items,String nextCursor) { }
    record Row(PublicContent card,long hearts) { }
    public record Cursor(long ceiling,long id,long hearts) {
        public String encode(Sort sort,Direction direction) { return "v1."+sort+"."+direction+"."+ceiling+"."+id+"."+hearts; }
        public static Cursor parse(String value,Sort sort,Direction direction) {
            try {
                if(value==null||value.length()>120)throw new IllegalArgumentException();
                // Keep old newest-first numeric links readable.
                if(value.matches("[0-9]{1,19}")&&sort==Sort.LATEST&&direction==Direction.DESC) {
                    long id=Long.parseLong(value);if(id<1)throw new IllegalArgumentException();return new Cursor(Long.MAX_VALUE,id,0);
                }
                String[] parts=value.split("\\.",-1);
                if(parts.length!=6||!parts[0].equals("v1")||!parts[1].equals(sort.name())||!parts[2].equals(direction.name()))throw new IllegalArgumentException();
                for(int i=3;i<6;i++)if(!parts[i].matches("[0-9]{1,19}"))throw new IllegalArgumentException();
                long ceiling=Long.parseLong(parts[3]),id=Long.parseLong(parts[4]),hearts=Long.parseLong(parts[5]);
                if(id<1||ceiling<id||hearts<0)throw new IllegalArgumentException();return new Cursor(ceiling,id,hearts);
            } catch(RuntimeException invalid) { throw ContentService.error(400,"INVALID_CURSOR","목록 위치를 확인해 주세요."); }
        }
    }
    private final JdbcTemplate db;
    public CheerFeedService(JdbcTemplate db){this.db=db;}
    private static final String PUBLIC="""
      FROM content_items i JOIN content_revisions r ON r.id=i.approved_revision_id
      JOIN users u ON u.id=i.author_id JOIN profiles p ON p.user_id=u.id
      WHERE i.kind='CHEER' AND i.visibility_status='VISIBLE' AND i.archived_at IS NULL
        AND r.moderation_status='APPROVED' AND u.status='ACTIVE' AND p.is_public=TRUE
        AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc
          ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
      """;
    // Counts use exactly the same eligibility as ReactionRepository.count/states.
    private static final String HEART_COUNTS="""
      LEFT JOIN (SELECT l.card_id,COUNT(*) AS heart_count FROM cheer_likes l
        JOIN users lu ON lu.id=l.user_id JOIN profiles lp ON lp.user_id=lu.id
        WHERE lu.status='ACTIVE' AND lp.is_public=TRUE
        AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc
          ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=lu.id AND uc.withdrawn_at IS NULL)
        GROUP BY l.card_id) h ON h.card_id=i.id
      """;
    public Page feed(Sort sort,Direction direction,String token) {
        if(sort==null||direction==null)throw ContentService.error(400,"INVALID_SORT","목록 정렬을 확인해 주세요.");
        Cursor cursor=token==null?null:Cursor.parse(token,sort,direction);
        long ceiling=cursor==null?Objects.requireNonNull(db.queryForObject("SELECT COALESCE(MAX(id),0) FROM content_items WHERE kind='CHEER'",Long.class)):cursor.ceiling();
        var args=new ArrayList<Object>();args.add(ceiling);
        boolean hearts=sort==Sort.HEARTS;String comparison=direction==Direction.ASC?">":"<";
        String from=hearts?PUBLIC.replace("WHERE i.kind",HEART_COUNTS+" WHERE i.kind"):PUBLIC;
        String inner="SELECT i.id,i.author_id,r.content_text,r.style,r.anonymous,p.nickname,"+(hearts?"COALESCE(h.heart_count,0)":"0")+" AS heart_count "+from+" AND i.id<=?";
        String boundary="";
        if(cursor!=null) {
            if(hearts){boundary=" WHERE (heart_count "+comparison+" ? OR (heart_count=? AND id "+comparison+" ?))";args.add(cursor.hearts());args.add(cursor.hearts());}
            else boundary=" WHERE id "+comparison+" ?";
            args.add(cursor.id());
        }
        String sql="SELECT * FROM ("+inner+") cards"+boundary+" ORDER BY "+(hearts?"heart_count "+direction.name()+",":"")+"id "+direction.name()+" LIMIT 21";
        var rows=db.query(sql,(r,n)->new Row(new PublicContent(r.getLong("id"),r.getString("content_text"),"PLAIN",r.getString("style"),r.getBoolean("anonymous"),r.getBoolean("anonymous")?null:r.getString("nickname"),null,null,r.getBoolean("anonymous")?null:r.getLong("author_id")),r.getLong("heart_count")),args.toArray());
        var page=rows.stream().limit(20).toList();Row last=page.isEmpty()?null:page.getLast();
        String next=rows.size()>20?new Cursor(ceiling,last.card().id(),last.hearts()).encode(sort,direction):null;
        return new Page(page.stream().map(Row::card).toList(),next);
    }
}
