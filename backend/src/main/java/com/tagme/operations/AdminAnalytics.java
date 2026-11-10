package com.tagme.operations;

import com.tagme.auth.*;
import com.tagme.participation.ParticipationModels.MissionCode;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalytics {
    public record Member(long id,long signupOrder,String username,String nickname,String realName,String region,Integer classNo,String status,Instant joinedAt,Instant lastLoginAt,int unlockedCount,int completedCount,Long completionOrder,Instant completedAt,Long connectionSeconds,Instant lastSeenAt,long activeSessions) { }
    public record Page<T>(List<T> items,long total,int offset,Integer nextOffset) { }
    public record MissionStat(String code,String title,long unlocked,long participated,long completed) { }
    public record Overview(long members,long activeMembers,long completeMembers,long activeSessions,long connectedMembers,Long connectionSeconds,Instant trackedSince,List<MissionStat> missions,Instant generatedAt) { }
    public record MissionDetail(String code,String title,String state,Instant unlockedAt,Instant completedAt,boolean participated,String resultStatus,Integer tapCount,String syncType,String moderationStatus) { }
    public record Connection(Instant startedAt,Instant lastSeenAt,Instant expiresAt,Instant endedAt,String status,long seconds) { }
    public record Detail(Member member,List<MissionDetail> missions,List<Connection> connections,String bio,List<String> interests,String githubUrl,String blogUrl) { }
    public record Rank(long rank,long userId,String nickname,String realName,String region,Integer classNo,long metric,Instant completedAt) { }
    public record Ranking(String mission,String basis,List<Rank> items,long total,int offset,Integer nextOffset) { }
    private final JdbcTemplate db;
    private final OperationsService operations;
    public AdminAnalytics(JdbcTemplate db,OperationsService operations){this.db=db;this.operations=operations;}
    private static Instant time(ResultSet r,String field)throws SQLException {var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
    private static final String PARTICIPANT="NOT EXISTS(SELECT 1 FROM local_admin_accounts la WHERE la.user_id=u.id)";
    private static final String DURATION="GREATEST(0,TIMESTAMPDIFF(SECOND,s.started_at,COALESCE(s.ended_at,LEAST(UTC_TIMESTAMP(6),s.expires_at))))";
    private static final String MEMBERS="""
        SELECT u.id,ROW_NUMBER() OVER(ORDER BY u.created_at,u.id) AS signup_order,u.mattermost_username,p.nickname,u.real_name,u.region,u.class_no,u.status,u.created_at,u.last_login_at,
          (SELECT COUNT(*) FROM mission_progress mp WHERE mp.user_id=u.id) AS unlocked_count,
          (SELECT COUNT(*) FROM mission_progress mp WHERE mp.user_id=u.id AND mp.status='COMPLETED') AS completed_count,
          ec.completion_order,ec.completed_at,
          (SELECT SUM(DURATION_VALUE) FROM connection_sessions s WHERE s.user_id=u.id) AS connection_seconds,
          (SELECT MAX(s.last_seen_at) FROM connection_sessions s WHERE s.user_id=u.id) AS last_seen_at,
          (SELECT COUNT(*) FROM connection_sessions s WHERE s.user_id=u.id AND s.ended_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6) AND u.status='ACTIVE' AND s.credential_version=credentials.credential_version) AS active_sessions
        FROM users u JOIN user_credentials credentials ON credentials.user_id=u.id LEFT JOIN profiles p ON p.user_id=u.id
        LEFT JOIN event_completions ec ON ec.user_id=u.id WHERE
        """.replace("DURATION_VALUE",DURATION)+PARTICIPANT;
    private Member member(ResultSet r,int n)throws SQLException {return new Member(r.getLong("id"),r.getLong("signup_order"),r.getString("mattermost_username"),r.getString("nickname"),r.getString("real_name"),r.getString("region"),r.getObject("class_no",Integer.class),r.getString("status"),time(r,"created_at"),time(r,"last_login_at"),r.getInt("unlocked_count"),r.getInt("completed_count"),r.getObject("completion_order",Long.class),time(r,"completed_at"),r.getObject("connection_seconds",Long.class),time(r,"last_seen_at"),r.getLong("active_sessions"));}
    private void paging(int offset,int limit){if(offset<0||offset>100000||limit<1||limit>50)throw new AuthException(400,"INVALID_PAGE","목록 범위를 확인해 주세요.");}
    @Transactional
    public Page<Member> members(AuthPrincipal actor,int offset,int limit,String q,boolean completed){
        operations.access(actor);paging(offset,limit);if(q!=null&&q.length()>64)throw new AuthException(400,"INVALID_SEARCH","검색어는 64자 이내로 입력해 주세요.");
        List<Object> args=new ArrayList<>();String filter=completed?" WHERE completion_order IS NOT NULL":" WHERE TRUE";
        if(q!=null&&!q.isBlank()){String value="%"+q.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%";filter+=" AND (nickname LIKE ? ESCAPE '!' OR real_name LIKE ? ESCAPE '!' OR mattermost_username LIKE ? ESCAPE '!')";args.addAll(List.of(value,value,value));}
        String source=" FROM ("+MEMBERS+") members"+filter;long total=db.queryForObject("SELECT COUNT(*)"+source,Long.class,args.toArray());
        args.add(limit);args.add(offset);var rows=db.query("SELECT *"+source+(completed?" ORDER BY completion_order,id":" ORDER BY signup_order")+" LIMIT ? OFFSET ?",this::member,args.toArray());
        return new Page<>(rows,total,offset,offset+rows.size()<total?offset+rows.size():null);
    }
    private static final String PARTICIPATED="""
        CASE m.code
          WHEN 'SHOW_ME' THEN mp.status='COMPLETED'
          WHEN 'SYNC_ME' THEN EXISTS(SELECT 1 FROM sync_results sr WHERE sr.user_id=u.id)
          WHEN 'FAST_TAP' THEN EXISTS(SELECT 1 FROM tap_slots ts WHERE ts.user_id=u.id AND ts.current_attempt_id IS NOT NULL)
          ELSE EXISTS(SELECT 1 FROM content_items ci WHERE ci.author_id=u.id AND ci.kind=IF(m.code='MAKE_A_WISH','WISH','CHEER')) END
        """;
    @Transactional
    public Overview overview(AuthPrincipal actor){
        operations.access(actor);
        long count=db.queryForObject("SELECT COUNT(*) FROM users u JOIN user_credentials c ON c.user_id=u.id WHERE "+PARTICIPANT,Long.class);
        long active=db.queryForObject("SELECT COUNT(*) FROM users u JOIN user_credentials c ON c.user_id=u.id WHERE "+PARTICIPANT+" AND u.status='ACTIVE'",Long.class);
        long complete=db.queryForObject("SELECT COUNT(*) FROM event_completions e JOIN users u ON u.id=e.user_id JOIN user_credentials c ON c.user_id=u.id WHERE "+PARTICIPANT,Long.class);
        record ConnectionAggregate(long active,long people,Long seconds,Instant since) { }
        var connection=db.query("SELECT COUNT(CASE WHEN s.ended_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6) AND u.status='ACTIVE' AND s.credential_version=c.credential_version THEN 1 END) AS active,COUNT(DISTINCT s.user_id) AS people,SUM("+DURATION+") AS seconds,MIN(s.started_at) AS since FROM connection_sessions s JOIN users u ON u.id=s.user_id JOIN user_credentials c ON c.user_id=u.id WHERE "+PARTICIPANT,(r,n)->{long seconds=r.getLong("seconds");Long totalSeconds=r.wasNull()?null:seconds;return new ConnectionAggregate(r.getLong("active"),r.getLong("people"),totalSeconds,time(r,"since"));}).getFirst();
        var missions=db.query("SELECT m.code,m.title,COUNT(mp.user_id) AS unlocked,COALESCE(SUM("+PARTICIPATED+"),0) AS participated,COALESCE(SUM(mp.status='COMPLETED'),0) AS completed FROM missions m LEFT JOIN mission_progress mp ON mp.mission_id=m.id AND EXISTS(SELECT 1 FROM user_credentials c WHERE c.user_id=mp.user_id) AND NOT EXISTS(SELECT 1 FROM local_admin_accounts la WHERE la.user_id=mp.user_id) LEFT JOIN users u ON u.id=mp.user_id GROUP BY m.id,m.code,m.title,m.sort_order ORDER BY m.sort_order",(r,n)->new MissionStat(r.getString("code"),r.getString("title"),r.getLong("unlocked"),r.getLong("participated"),r.getLong("completed")));
        return new Overview(count,active,complete,connection.active(),connection.people(),connection.seconds(),connection.since(),missions,Instant.now());
    }
    @Transactional
    public Detail detail(AuthPrincipal actor,long id){
        operations.access(actor);
        var person=db.query("SELECT * FROM ("+MEMBERS+") members WHERE id=?",this::member,id).stream().findFirst().orElseThrow(()->new AuthException(404,"MEMBER_NOT_FOUND","참가자를 찾을 수 없어요."));
        var missions=db.query("""
            SELECT m.code,m.title,COALESCE(mp.status,'LOCKED') AS state,mp.unlocked_at,mp.completed_at,
              """+PARTICIPATED+"""
              AS participated,t.status AS result_status,t.tap_count,sr.type_code,cr.moderation_status
            FROM users u CROSS JOIN missions m LEFT JOIN mission_progress mp ON mp.user_id=u.id AND mp.mission_id=m.id
            LEFT JOIN sync_results sr ON sr.user_id=u.id AND m.code='SYNC_ME'
            LEFT JOIN tap_slots ts ON ts.user_id=u.id AND m.code='FAST_TAP' LEFT JOIN tap_attempts t ON t.id=ts.current_attempt_id
            LEFT JOIN content_items ci ON ci.author_id=u.id AND ci.kind=CASE m.code WHEN 'MAKE_A_WISH' THEN 'WISH' WHEN 'SAY_SOMETHING' THEN 'CHEER' ELSE NULL END
            LEFT JOIN content_revisions cr ON cr.id=COALESCE(ci.pending_revision_id,ci.approved_revision_id)
            WHERE u.id=? ORDER BY m.sort_order
            """,(r,n)->new MissionDetail(r.getString("code"),r.getString("title"),r.getString("state"),time(r,"unlocked_at"),time(r,"completed_at"),r.getBoolean("participated"),r.getString("result_status"),r.getObject("tap_count",Integer.class),r.getString("type_code"),r.getString("moderation_status")),id);
        var sessions=db.query("SELECT s.*,"+DURATION+" AS seconds,CASE WHEN ended_at IS NOT NULL THEN end_reason WHEN expires_at<=UTC_TIMESTAMP(6) THEN 'EXPIRED' ELSE 'CONNECTED' END AS status FROM connection_sessions s WHERE user_id=? ORDER BY started_at DESC,id DESC LIMIT 30",(r,n)->new Connection(time(r,"started_at"),time(r,"last_seen_at"),time(r,"expires_at"),time(r,"ended_at"),r.getString("status"),r.getLong("seconds")),id);
        var profile=db.queryForList("SELECT bio,github_url,blog_url FROM profiles WHERE user_id=?",id);
        var interests=db.queryForList("SELECT i.name FROM interests i JOIN user_interests ui ON ui.interest_id=i.id WHERE ui.user_id=? ORDER BY i.id",String.class,id);
        var p=profile.isEmpty()?Map.<String,Object>of():profile.getFirst();
        return new Detail(person,missions,sessions,(String)p.get("bio"),interests,(String)p.get("github_url"),(String)p.get("blog_url"));
    }
    private String eligible(String alias){return alias+".status='ACTIVE' AND "+alias+".mattermost_profile_synced_at IS NOT NULL AND TRIM(COALESCE("+alias+".real_name,''))<>'' AND TRIM(COALESCE("+alias+".region,''))<>'' AND "+alias+".class_no>0 AND EXISTS(SELECT 1 FROM profiles vp WHERE vp.user_id="+alias+".id AND vp.is_public=TRUE AND TRIM(vp.nickname)<>'') AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id="+alias+".id AND uc.withdrawn_at IS NULL)";}
    @Transactional
    public Ranking ranking(AuthPrincipal actor,String mission,int offset,int limit){
        operations.access(actor);paging(offset,limit);MissionCode code;try{code=MissionCode.valueOf(mission);}catch(IllegalArgumentException invalid){throw new AuthException(400,"INVALID_MISSION","미션을 확인해 주세요.");}
        String metric="0",extra="",basis="완료 시각이 빠른 순",order="completed_at,user_id";
        switch(code){
            case FAST_TAP -> {metric="t.tap_count";extra=" JOIN tap_slots ts ON ts.user_id=u.id JOIN tap_attempts t ON t.id=ts.current_attempt_id AND t.status='VALID'";basis="현재 유효 공식 도전의 탭 수 · 동점은 유효 제출 시각, 회원 ID 순";order="metric DESC,sort_at,user_id";}
            case MAKE_A_WISH -> {metric="(SELECT COUNT(*) FROM wish_comments wc JOIN content_items c ON c.id=wc.content_item_id AND c.visibility_status='VISIBLE' JOIN content_revisions r ON r.id=c.approved_revision_id AND r.moderation_status='APPROVED' JOIN users author ON author.id=wc.author_id WHERE wc.wish_id=item.id AND wc.author_id<>u.id AND "+eligible("author")+")";extra=" JOIN content_items item ON item.author_id=u.id AND item.kind='WISH' AND item.visibility_status='VISIBLE' JOIN content_revisions approved ON approved.id=item.approved_revision_id AND approved.moderation_status='APPROVED'";basis="받은 유효 승인 댓글 수 · 자기 댓글 제외, 동점은 완료 시각 순";order="metric DESC,completed_at,user_id";}
            case SAY_SOMETHING -> {metric="(SELECT COUNT(*) FROM cheer_likes likes JOIN users liker ON liker.id=likes.user_id WHERE likes.card_id=item.id AND likes.user_id<>u.id AND "+eligible("liker")+")";extra=" JOIN content_items item ON item.author_id=u.id AND item.kind='CHEER' AND item.visibility_status='VISIBLE' JOIN content_revisions approved ON approved.id=item.approved_revision_id AND approved.moderation_status='APPROVED'";basis="받은 유효 하트 수 · 자기 하트 제외, 동점은 완료 시각 순";order="metric DESC,completed_at,user_id";}
            default -> { }
        }
        String base="SELECT u.id AS user_id,p.nickname,u.real_name,u.region,u.class_no,mp.completed_at,"+(code==MissionCode.FAST_TAP?"t.submitted_at":"mp.completed_at")+" AS sort_at,"+metric+" AS metric FROM users u JOIN user_credentials credentials ON credentials.user_id=u.id JOIN profiles p ON p.user_id=u.id JOIN mission_progress mp ON mp.user_id=u.id AND mp.status='COMPLETED' JOIN missions m ON m.id=mp.mission_id"+extra+" WHERE "+PARTICIPANT+" AND "+eligible("u")+" AND m.code=?";
        long total=db.queryForObject("SELECT COUNT(*) FROM ("+base+") ranked",Long.class,mission);
        var rows=db.query("SELECT ranked.*,ROW_NUMBER() OVER(ORDER BY "+order+") AS rank_no FROM ("+base+") ranked ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->new Rank(r.getLong("rank_no"),r.getLong("user_id"),r.getString("nickname"),r.getString("real_name"),r.getString("region"),r.getObject("class_no",Integer.class),r.getLong("metric"),time(r,"completed_at")),mission,limit,offset);
        return new Ranking(mission,basis,rows,total,offset,offset+rows.size()<total?offset+rows.size():null);
    }
}
