package com.tagme.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.results.Questionnaire;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import static com.tagme.operations.PostEventModels.*;

/** Read-only projections. No participant bodies, external identities or raw survey answers. */
@Repository
public class PostEventRepository {
    private final JdbcTemplate db; private final ObjectMapper json;
    public PostEventRepository(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    public Instant now(){return db.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant();}
    // Scope is explicit: current active USER accounts, cumulative records <= snapshot.
    // Current publication/consent eligibility is a separate subset, never inferred from a grant.
    private static final String BASE="""
      WITH clock AS (SELECT ? at_time), cohort AS (
        SELECT u.id FROM users u,clock WHERE u.role='USER' AND u.status='ACTIVE' AND u.created_at<=clock.at_time
      ), public_users AS (
        SELECT c.id FROM cohort c JOIN users u ON u.id=c.id JOIN profiles p ON p.user_id=c.id
        WHERE p.is_public=TRUE AND TRIM(p.nickname)<>'' AND TRIM(COALESCE(u.real_name,''))<>''
          AND TRIM(COALESCE(u.region,''))<>'' AND u.class_no>0 AND u.mattermost_profile_synced_at IS NOT NULL
          AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc ON uc.policy_type=cp.policy_type
            AND uc.policy_version=cp.policy_version WHERE uc.user_id=c.id AND uc.withdrawn_at IS NULL)
      ), ready AS (
        SELECT u.id FROM public_users u JOIN profiles p ON p.user_id=u.id
        WHERE TRIM(COALESCE(p.bio,''))<>'' AND EXISTS(SELECT 1 FROM user_interests i WHERE i.user_id=u.id)
      ), live AS (
        SELECT i.id,i.author_id,i.kind FROM content_items i JOIN public_users u ON u.id=i.author_id
        JOIN content_revisions r ON r.id=i.approved_revision_id AND r.item_id=i.id
        JOIN moderation_jobs j ON j.revision_id=r.id AND j.status='APPROVED',clock
        WHERE i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED' AND r.submitted_at<=clock.at_time
      ), grants AS (
        SELECT p.user_id,p.mission_id,p.completed_at FROM mission_progress p JOIN cohort c ON c.id=p.user_id,clock
        WHERE p.status='COMPLETED' AND p.completed_at<=clock.at_time
      )
      """;
    private <T> List<T> query(Instant at,String sql,RowMapper<T> mapper,Object...args){var values=new ArrayList<Object>();values.add(Timestamp.from(at));values.addAll(Arrays.asList(args));return db.query(BASE+sql,mapper,values.toArray());}
    private long count(Instant at,String sql){return query(at,sql,(r,n)->r.getLong(1)).getFirst();}
    private Metric metric(Instant at,String key,String sql,String basis){return new Metric(key,count(at,sql),"명",basis);}
    public List<Metric> metrics(Instant at){
        var m=new ArrayList<Metric>();
        m.add(metric(at,"registered","SELECT COUNT(*) FROM cohort","현재 ACTIVE·USER 가입 계정 ID 중복 제거. 탈퇴·정지·ADMIN 제외; 기간 누적"));
        m.add(metric(at,"profileReady","SELECT COUNT(*) FROM ready","등록자 중 현재 MM 정보·공개·최신 두 동의·닉네임·소개·관심사 충족"));
        m.add(metric(at,"activeParticipants","SELECT COUNT(DISTINCT user_id) FROM (SELECT p.user_id FROM mission_progress p JOIN cohort c ON c.id=p.user_id UNION SELECT i.author_id FROM content_items i JOIN cohort c ON c.id=i.author_id WHERE i.kind IN ('WISH','CHEER') UNION SELECT s.user_id FROM sync_results s JOIN cohort c ON c.id=s.user_id UNION SELECT t.user_id FROM tap_attempts t JOIN cohort c ON c.id=t.user_id) a","등록자 중 해금/지급·설문·공식 도전·소원/응원 접수 기록이 하나 이상 있는 고유 ID; 페이지 방문 아님"));
        m.add(metric(at,"earnedAny","SELECT COUNT(DISTINCT user_id) FROM grants","등록자 중 서버 COMPLETED 글자 1개 이상. 사후 시상 자격과 별개"));
        m.add(metric(at,"earnedFive","SELECT COUNT(*) FROM (SELECT user_id FROM grants GROUP BY user_id HAVING COUNT(*)=5) g","등록자 중 서버 COMPLETED 5종. 시상 유효 근거는 별도 재검증"));
        m.add(metric(at,"showAuto","SELECT COUNT(DISTINCT e.user_id) FROM mission_completion_evidence e JOIN grants g ON g.user_id=e.user_id AND g.mission_id=e.mission_id JOIN missions m ON m.id=g.mission_id WHERE m.code='SHOW_ME' AND e.source_kind='PROFILE'","첫 S 지급 원장이 PROFILE인 고유 등록자; 기존 TAG_UNLOCK 지급과 구분"));
        m.add(metric(at,"showReadyUnpaid","SELECT COUNT(*) FROM ready r WHERE NOT EXISTS(SELECT 1 FROM grants g JOIN missions m ON m.id=g.mission_id WHERE g.user_id=r.id AND m.code='SHOW_ME')","현재 프로필 요건 충족하나 S 미지급; 자동 지급/운영 조치하지 않음"));
        m.add(metric(at,"preRegisteredUnpaid","SELECT COUNT(*) FROM ready r JOIN profiles p ON p.user_id=r.id JOIN event_settings e ON e.id=1 WHERE p.published_at<e.opens_at AND NOT EXISTS(SELECT 1 FROM grants g JOIN missions m ON m.id=g.mission_id WHERE g.user_id=r.id AND m.code='SHOW_ME')","최초 공개가 OPEN 전이고 현재 요건 충족·S 미지급. 재평가 실행 여부 로그는 없음"));
        m.add(metric(at,"syncSubmitted","SELECT COUNT(*) FROM sync_results s JOIN cohort c ON c.id=s.user_id,clock WHERE s.submitted_at<=clock.at_time","최초 설문 저장 고유 사용자; 유형 유효성은 별도 분포"));
        m.add(metric(at,"tapParticipants","SELECT COUNT(DISTINCT t.user_id) FROM tap_attempts t JOIN cohort c ON c.id=t.user_id","공식 도전 행이 존재하는 고유 참가자; COUNTDOWN/취소도 포함하며 실제 터치 인원 아님"));
        m.add(metric(at,"wishAuthors","SELECT COUNT(DISTINCT author_id) FROM content_items i JOIN cohort c ON c.id=i.author_id,clock WHERE i.kind='WISH' AND i.created_at<=clock.at_time","최초 소원 슬롯 작성자; 수정본 수와 구분"));
        m.add(metric(at,"cheerAuthors","SELECT COUNT(DISTINCT author_id) FROM content_items i JOIN cohort c ON c.id=i.author_id,clock WHERE i.kind='CHEER' AND i.created_at<=clock.at_time","독립 응원 슬롯 작성자; 소원 댓글 아님"));
        for(String kind:List.of("WISH","CHEER","COMMENT"))m.add(new Metric("public"+kind,count(at,"SELECT COUNT(*) FROM live WHERE kind='"+kind+"'"+(kind.equals("COMMENT")?" AND EXISTS(SELECT 1 FROM wish_comments c JOIN live w ON w.id=c.wish_id AND w.kind='WISH' WHERE c.content_item_id=live.id AND c.author_id<>w.author_id)":"")),"건","현재 승인본·VISIBLE·현재 공개 자격. 댓글은 공개 원글과 타인 댓글만"));
        m.add(new Metric("otherWishComments",count(at,"SELECT COUNT(*) FROM wish_comments c JOIN cohort u ON u.id=c.author_id JOIN content_items w ON w.id=c.wish_id WHERE c.author_id<>w.author_id"),"건","타인의 소원 댓글 슬롯. 같은 소원 중복 댓글은 별개 건, 공개 가능 건은 publicCOMMENT"));
        m.add(new Metric("validHearts",count(at,"SELECT COUNT(*) FROM cheer_likes h JOIN live card ON card.id=h.card_id AND card.kind='CHEER' JOIN public_users voter ON voter.id=h.user_id,clock WHERE h.user_id<>card.author_id AND h.created_at<=clock.at_time"),"건","현재 남아 있는 (응원 카드,공개 자격 투표자) PK. 취소는 행 삭제, 자기 하트 제외"));
        m.add(new Metric("visitRelations",count(at,"SELECT COUNT(*) FROM profile_views v JOIN public_users a ON a.id=v.viewer_id JOIN public_users b ON b.id=v.viewed_id,clock WHERE v.first_viewed_at<=clock.at_time"),"관계","현재 공개 가능 양끝의 고유 방향 관계; 반복 방문 수 아님"));
        m.add(new Metric("mutualRelations",count(at,"SELECT COUNT(*) FROM profile_views a JOIN profile_views b ON b.viewer_id=a.viewed_id AND b.viewed_id=a.viewer_id JOIN public_users x ON x.id=a.viewer_id JOIN public_users y ON y.id=a.viewed_id,clock WHERE a.viewer_id<a.viewed_id AND a.first_viewed_at<=clock.at_time AND b.first_viewed_at<=clock.at_time"),"쌍","양방향 최초 방문 존재; 작은 ID<큰 ID로 쌍 중복 제거"));
        return List.copyOf(m);
    }
    public List<Mission> missions(Instant at){return query(at,"""
      SELECT m.code,
        (SELECT COUNT(DISTINCT p.user_id) FROM mission_progress p JOIN cohort c ON c.id=p.user_id,clock WHERE p.mission_id=m.id AND p.unlocked_at<=clock.at_time) unlocked,
        CASE m.code
          WHEN 'SHOW_ME' THEN (SELECT COUNT(*) FROM ready)
          WHEN 'SYNC_ME' THEN (SELECT COUNT(*) FROM sync_results s JOIN cohort c ON c.id=s.user_id,clock WHERE s.submitted_at<=clock.at_time)
          WHEN 'FAST_TAP' THEN (SELECT COUNT(DISTINCT t.user_id) FROM tap_attempts t JOIN cohort c ON c.id=t.user_id)
          ELSE (SELECT COUNT(DISTINCT i.author_id) FROM content_items i JOIN cohort c ON c.id=i.author_id,clock WHERE i.kind=IF(m.code='MAKE_A_WISH','WISH','CHEER') AND i.created_at<=clock.at_time) END performed,
        (SELECT COUNT(*) FROM grants g WHERE g.mission_id=m.id) completed,
        (SELECT COUNT(DISTINCT i.author_id) FROM content_items i JOIN cohort c ON c.id=i.author_id JOIN content_revisions r ON r.id=i.pending_revision_id JOIN moderation_jobs j ON j.revision_id=r.id,clock WHERE i.kind=CASE m.code WHEN 'MAKE_A_WISH' THEN 'WISH' WHEN 'SAY_SOMETHING' THEN 'CHEER' ELSE 'NONE' END AND j.status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED') AND r.submitted_at<=clock.at_time) pending
      FROM missions m ORDER BY m.sort_order
      """,(r,n)->new Mission(r.getString("code"),r.getString("code").equals("SHOW_ME")?null:r.getLong("unlocked"),r.getLong("performed"),r.getLong("completed"),r.getLong("pending"),"분모=현재 ACTIVE·USER 등록자. 각 단계 독립 고유 인원; 수행-완료 차이를 이탈로 단정하지 않음. SHOW ME 해금 해당 없음"));}
    public List<Bucket> buckets(Instant at){
        var rows=new ArrayList<Bucket>(query(at,"SELECT amount,COUNT(*) n FROM (SELECT c.id,COUNT(g.mission_id) amount FROM cohort c LEFT JOIN grants g ON g.user_id=c.id GROUP BY c.id) a GROUP BY amount ORDER BY amount",(r,n)->new Bucket("LETTERS",r.getString("amount"),r.getLong("n"))));
        rows.addAll(query(at,"SELECT i.kind,j.status,COUNT(*) n FROM moderation_jobs j JOIN content_revisions r ON r.id=j.revision_id JOIN content_items i ON i.id=r.item_id JOIN cohort c ON c.id=i.author_id,clock WHERE r.submitted_at<=clock.at_time GROUP BY i.kind,j.status ORDER BY i.kind,j.status",(r,n)->new Bucket("MODERATION_REVISIONS",r.getString("kind")+":"+r.getString("status"),r.getLong("n"))));
        rows.addAll(query(at,"SELECT i.kind,COUNT(*) n FROM content_items i JOIN cohort c ON c.id=i.author_id WHERE i.visibility_status='HIDDEN' GROUP BY i.kind ORDER BY i.kind",(r,n)->new Bucket("HIDDEN_ITEMS",r.getString("kind"),r.getLong("n"))));
        rows.addAll(query(at,"SELECT t.status,COUNT(*) n FROM tap_attempts t JOIN cohort c ON c.id=t.user_id GROUP BY t.status ORDER BY t.status",(r,n)->new Bucket("TAP_ALL_STORED",r.getString("status"),r.getLong("n"))));
        rows.addAll(query(at,"SELECT t.status,COUNT(*) n FROM tap_slots s JOIN tap_attempts t ON t.id=s.current_attempt_id JOIN cohort c ON c.id=t.user_id GROUP BY t.status ORDER BY t.status",(r,n)->new Bucket("TAP_CURRENT_STORED",r.getString("status"),r.getLong("n"))));
        rows.addAll(query(at,"SELECT FLOOR(t.tap_count/25)*25 band,COUNT(*) n FROM tap_slots s JOIN tap_attempts t ON t.id=s.current_attempt_id JOIN cohort c ON c.id=t.user_id WHERE t.status='VALID' GROUP BY band ORDER BY band",(r,n)->new Bucket("TAP_STORED_VALID_SCORE",r.getString("band")+"~"+(r.getLong("band")+24),r.getLong("n"))));
        // Types are bounded by published questionnaires; no raw answers are read or exported.
        var definitions=new HashMap<String,Questionnaire>();
        db.query("SELECT version,definition_json FROM sync_questionnaires",r->{try{definitions.put(r.getString(1),Questionnaire.parse(r.getString(2),json));}catch(IllegalArgumentException ignored){/* invalid definition remains explicitly invalid */}});
        var types=new TreeMap<String,Long>();
        query(at,"SELECT s.questionnaire_version,s.type_code,COUNT(*) n FROM sync_results s JOIN cohort c ON c.id=s.user_id,clock WHERE s.submitted_at<=clock.at_time GROUP BY s.questionnaire_version,s.type_code",(r,n)->{String version=r.getString(1),code=r.getString(2);var q=definitions.get(version);String key=q!=null&&q.types().stream().anyMatch(t->t.code().equals(code))?version+":"+code:"INVALID_DEFINITION_OR_TYPE";types.merge(key,r.getLong(3),Long::sum);return key;});
        types.forEach((key,value)->rows.add(new Bucket("SYNC_VERSION_TYPE",key,value)));
        rows.add(new Bucket("SYNC_CONFIG","CURRENT_DEFINITION_READY",db.queryForObject("SELECT COUNT(*) FROM current_sync_questionnaire c JOIN sync_questionnaires q ON q.version=c.version",Long.class)==1&&definitions.containsKey(db.queryForObject("SELECT version FROM current_sync_questionnaire WHERE id=1",String.class))?1:0));
        return List.copyOf(rows);
    }
    public List<Hour> hours(Instant at){return query(at,"""
      , activity AS (
        SELECT u.id user_id,u.created_at time,'SIGNUP' event FROM users u JOIN cohort c ON c.id=u.id
        UNION ALL SELECT p.user_id,p.published_at,'PROFILE_FIRST_PUBLICATION' FROM profiles p JOIN cohort c ON c.id=p.user_id WHERE p.published_at IS NOT NULL
        UNION ALL SELECT p.user_id,p.unlocked_at,'NFC_FIRST_UNLOCK' FROM mission_progress p JOIN missions m ON m.id=p.mission_id JOIN cohort c ON c.id=p.user_id WHERE m.code<>'SHOW_ME'
        UNION ALL SELECT g.user_id,g.completed_at,'LETTER_GRANTED' FROM grants g
        UNION ALL SELECT i.author_id,i.created_at,IF(i.kind='WISH','WISH_FIRST_SUBMISSION','CHEER_FIRST_SUBMISSION') FROM content_items i JOIN cohort c ON c.id=i.author_id WHERE i.kind IN ('WISH','CHEER')
        UNION ALL SELECT s.user_id,s.submitted_at,'SYNC_SUBMITTED' FROM sync_results s JOIN cohort c ON c.id=s.user_id
        UNION ALL SELECT t.user_id,t.starts_at,'TAP_SCHEDULED' FROM tap_attempts t JOIN cohort c ON c.id=t.user_id
        UNION ALL SELECT t.user_id,t.submitted_at,'TAP_SUBMITTED' FROM tap_attempts t JOIN cohort c ON c.id=t.user_id WHERE t.submitted_at IS NOT NULL
        UNION ALL SELECT i.author_id,r.submitted_at,CONCAT(i.kind,'_REVISION_SUBMITTED') FROM content_revisions r JOIN content_items i ON i.id=r.item_id JOIN cohort c ON c.id=i.author_id
        UNION ALL SELECT i.author_id,v.created_at,'MODERATION_DECISION' FROM moderation_reviews v JOIN moderation_jobs j ON j.id=v.job_id JOIN content_revisions r ON r.id=j.revision_id JOIN content_items i ON i.id=r.item_id JOIN cohort c ON c.id=i.author_id
        UNION ALL SELECT h.user_id,h.created_at,'HEART_CURRENT' FROM cheer_likes h JOIN cohort c ON c.id=h.user_id
        UNION ALL SELECT v.viewer_id,v.first_viewed_at,'PROFILE_FIRST_VISIT' FROM profile_views v JOIN cohort c ON c.id=v.viewer_id
        UNION ALL SELECT e.user_id,e.completed_at,'ALL_COMPLETED' FROM event_completions e JOIN cohort c ON c.id=e.user_id
      ) SELECT DATE_FORMAT(a.time,'%Y-%m-%dT%H:00:00Z') hour_utc,a.event,COUNT(DISTINCT a.user_id) participants,COUNT(*) records
        FROM activity a,clock WHERE a.time<=clock.at_time GROUP BY hour_utc,a.event ORDER BY hour_utc DESC,a.event LIMIT 1001
      """,(r,n)->new Hour(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4)));}
    public boolean participantExists(long id,Instant at){return !query(at,"SELECT id FROM cohort WHERE id=?",(r,n)->r.getLong(1),id).isEmpty();}
    public Instant firstProfile(long id){return db.query("SELECT published_at FROM profiles WHERE user_id=?",(r,n)->r.getTimestamp(1)==null?null:r.getTimestamp(1).toInstant(),id).stream().filter(Objects::nonNull).findFirst().orElse(null);}
    public List<Step> steps(long id,Instant at,List<AwardModels.Proof> proofs){return query(at,"SELECT m.code,p.status,p.unlocked_at,p.completed_at FROM missions m LEFT JOIN mission_progress p ON p.mission_id=m.id AND p.user_id=? ORDER BY m.sort_order",(r,n)->{
        String code=r.getString("code");var proof=proofs.stream().filter(p->p.userId()==id&&p.mission().equals(code)).findFirst().orElse(null);
        return new Step(r.getString("code"),r.getString("status")==null?"LOCKED":r.getString("status"),instant(r,"unlocked_at"),instant(r,"completed_at"),proof==null?null:proof.sourceKind(),proof==null?null:proof.sourceId(),proof==null?null:proof.acceptedAt(),proof!=null&&proof.valid());},id);}
    private static Instant instant(ResultSet r,String key)throws SQLException{var t=r.getTimestamp(key);return t==null?null:t.toInstant();}
    public List<Activity> receipts(long id,long after,Instant at){return query(at,"""
      SELECT r.id,r.source_kind,r.received_at,m.code FROM submission_receipts r
      LEFT JOIN missions m ON m.code=CASE r.source_kind WHEN 'PROFILE' THEN 'SHOW_ME' WHEN 'SYNC_RESULT' THEN 'SYNC_ME' WHEN 'WISH_REVISION' THEN 'MAKE_A_WISH' WHEN 'CHEER_REVISION' THEN 'SAY_SOMETHING' WHEN 'GAME_ATTEMPT' THEN 'FAST_TAP' END OR (r.source_kind='TAG_UNLOCK' AND m.id=r.source_id),clock
      WHERE r.user_id=? AND r.id>? AND r.received_at<=clock.at_time ORDER BY r.id LIMIT 51
      """,(r,n)->new Activity(r.getLong(1),"RECEIPT",r.getString(4),r.getTimestamp(3).toInstant(),"ACCEPTED_RECEIPT_NOT_REWARD",r.getString(2)),id,after);}
}
