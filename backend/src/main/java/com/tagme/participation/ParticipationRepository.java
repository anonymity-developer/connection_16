package com.tagme.participation;

import com.tagme.auth.AuthException;
import com.tagme.auth.AuthPrincipal;
import com.tagme.auth.AuthRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.tagme.participation.ParticipationModels.*;

@Repository
public class ParticipationRepository {
    private final JdbcTemplate db;
    private final AuthRepository accounts;
    public ParticipationRepository(JdbcTemplate db, AuthRepository accounts) { this.db = db; this.accounts = accounts; }
    public long generation(long user) {
        return db.queryForObject("SELECT COALESCE(MAX(generation),0) FROM game_participation_resets WHERE user_id=?",Long.class,user);
    }
    /** Call only after locking the participant, so reset and delayed writes serialize. */
    public void requireGeneration(long user,long expected) {
        if(expected<0||generation(user)!=expected)throw error(409,"PARTICIPATION_RESET","관리자가 게임 참여를 초기화했어요. 화면을 새로고침한 뒤 다시 참여해 주세요.");
    }
    static AuthException error(int status, String code, String message) { return new AuthException(status, code, message); }
    public void lockUser(long id) {
        accounts.lockUser(id);
        var account = accounts.byId(id).orElseThrow(AuthException::invalid);
        if (!"ACTIVE".equals(account.status()) || account.hash() == null) throw AuthException.invalid();
    }
    public void lockPrincipal(AuthPrincipal principal) {
        lockUser(principal.userId());
        if (!Objects.equals(accounts.byId(principal.userId()).orElseThrow(AuthException::invalid).version(), principal.version())) throw AuthException.invalid();
    }
    static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    public Event event(boolean lock) {
        return db.query("SELECT *, UTC_TIMESTAMP(6) AS current_time_utc FROM event_settings WHERE id=1" + (lock ? " FOR SHARE" : ""), (row, n) -> {
            Instant now = instant(row, "current_time_utc"), opens = instant(row, "opens_at"), closes = instant(row, "submit_closes_at");
            return new Event(now.isBefore(opens) ? "BEFORE_OPEN" : now.isBefore(closes) ? "OPEN" : "CLOSED", opens, closes, row.getBoolean("profile_preparation_enabled"));
        }).stream().findFirst().orElse(new Event("NOT_CONFIGURED", null, null, false));
    }
    public void requireOpen(boolean profile) {
        var event = event(true);
        if ("NOT_CONFIGURED".equals(event.status())) throw error(503, "EVENT_NOT_CONFIGURED", "행사 일정을 준비하고 있어요.");
        if (!"OPEN".equals(event.status()) && !(profile && "BEFORE_OPEN".equals(event.status()) && event.profilePreparationEnabled()))
            throw error(403, "EVENT_CLOSED", "지금은 참여 가능한 시간이 아니에요.");
    }
    public Instant now() { return db.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant(); }
    /** One DB clock sample both checks the half-open event window and dates a new SHOW ME grant. */
    public Instant requireOpenTime() {
        return db.query("SELECT opens_at,submit_closes_at,UTC_TIMESTAMP(6) AS processed_at FROM event_settings WHERE id=1 FOR SHARE",(row,n) -> {
            Instant processed=instant(row,"processed_at");
            if (processed.isBefore(instant(row,"opens_at")) || !processed.isBefore(instant(row,"submit_closes_at")))
                throw error(403,"EVENT_CLOSED","지금은 참여 가능한 시간이 아니에요.");
            return processed;
        }).stream().findFirst().orElseThrow(() -> error(503,"EVENT_NOT_CONFIGURED","행사 일정을 준비하고 있어요."));
    }
    public List<Policy> policies(long userId, boolean lock) {
        var policies = db.query("""
                SELECT p.* FROM current_consent_policies c JOIN consent_policies p
                ON p.policy_type=c.policy_type AND p.policy_version=c.policy_version ORDER BY p.policy_type
                """ + (lock ? " FOR SHARE" : ""), (row, n) -> new Policy(PolicyType.valueOf(row.getString("policy_type")), row.getString("policy_version"), row.getString("title"), row.getString("body"), false));
        return policies.stream().map(p -> new Policy(p.type(), p.version(), p.title(), p.body(), agreed(userId, p.type(), p.version()))).toList();
    }
    public Policies policyState(long userId, boolean lock) {
        var policies = policies(userId, lock); return new Policies(policies.size() == PolicyType.values().length, policies);
    }
    public boolean agreed(long userId, PolicyType type, String version) {
        return db.queryForObject("SELECT COUNT(*) FROM user_consents WHERE user_id=? AND policy_type=? AND policy_version=? AND withdrawn_at IS NULL", Integer.class, userId, type.name(), version) == 1;
    }
    public void agree(long userId, PolicyType type, String version) {
        if (agreed(userId, type, version)) return;
        db.update("""
                INSERT INTO user_consents(user_id,policy_type,policy_version,agreed_at) VALUES (?,?,?,UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE agreed_at=UTC_TIMESTAMP(6),withdrawn_at=NULL
                """, userId, type.name(), version);
        db.update("""
                INSERT INTO user_consent_events(consent_id,action,occurred_at)
                SELECT id,'AGREED',UTC_TIMESTAMP(6) FROM user_consents WHERE user_id=? AND policy_type=? AND policy_version=?
                """, userId, type.name(), version);
    }
    public void withdraw(long userId, PolicyType type, String version) {
        if (!agreed(userId, type, version)) return;
        db.update("UPDATE user_consents SET withdrawn_at=UTC_TIMESTAMP(6) WHERE user_id=? AND policy_type=? AND policy_version=?", userId, type.name(), version);
        db.update("""
                INSERT INTO user_consent_events(consent_id,action,occurred_at)
                SELECT id,'WITHDRAWN',UTC_TIMESTAMP(6) FROM user_consents WHERE user_id=? AND policy_type=? AND policy_version=?
                """, userId, type.name(), version);
    }
    public void withdrawAll(long userId,PolicyType type) {
        var versions = db.queryForList("SELECT policy_version FROM user_consents WHERE user_id=? AND policy_type=? AND withdrawn_at IS NULL",String.class,userId,type.name());
        versions.forEach(version -> withdraw(userId,type,version));
    }
    public List<CatalogInterest> interests() { return db.query("SELECT id,name,category,group_name FROM interests WHERE active=TRUE ORDER BY FIELD(category,'INDUSTRY','DEVELOPMENT'),display_order,id", (row,n) -> new CatalogInterest(row.getLong("id"), row.getString("name"),row.getString("category"),row.getString("group_name"))); }
    public boolean discoveryReady(long id) {
        return db.queryForObject("SELECT COUNT(*) FROM profiles p WHERE p.user_id=? AND TRIM(COALESCE(p.bio,''))<>'' AND "+InterestCatalog.readySql("p.user_id"),Integer.class,id)==1;
    }
    public OwnProfile profile(long id) {
        return db.query("""
                SELECT p.*,u.real_name,u.region,u.class_no,u.mattermost_profile_synced_at FROM profiles p JOIN users u ON u.id=p.user_id WHERE p.user_id=?
                """, (row,n) -> new OwnProfile(row.getString("nickname"), row.getString("real_name"), row.getString("region"), row.getObject("class_no",Integer.class),
                row.getString("bio"), row.getString("github_url"), row.getString("blog_url"), row.getBoolean("is_public"), row.getLong("version"),
                db.queryForList("SELECT interest_id FROM user_interests WHERE user_id=? ORDER BY interest_id", Long.class,id),row.getTimestamp("mattermost_profile_synced_at")!=null), id).stream().findFirst().orElse(null);
    }
    public Identity identity(long id) {
        var source=accounts.profileIdentity(id);
        return new Identity(source.realName(),source.region(),source.classNo(),source.pictureVersion()>0?"/api/v1/users/me/photo?v="+source.pictureVersion():null,source.ready());
    }
    public boolean profileComplete(OwnProfile profile) {
        return profile != null && present(profile.nickname()) && present(profile.realName()) && profile.sourceReady() && present(profile.region())
                && profile.classNo() != null && profile.classNo() > 0 ;
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
    public boolean eligible(long userId) {
        var profile = profile(userId); var policies = policyState(userId, true);
        return profileComplete(profile) && profile.isPublic() && policies.ready() && policies.policies().stream().allMatch(Policy::agreed);
    }
    public boolean showMeEligible(long userId) {
    if (!eligible(userId)) return false;

    var profile = profile(userId);

    return present(profile.bio())
            && !profile.interestIds().isEmpty() && discoveryReady(userId);
    }
    public boolean showMeRegrantBlocked(long userId) { return db.queryForObject("SELECT COUNT(*) FROM show_me_regrant_blocks WHERE user_id=?",Integer.class,userId)>0; }
    public void allowShowMeRegrant(long userId) { db.update("DELETE FROM show_me_regrant_blocks WHERE user_id=?",userId); }
    public void hide(long userId) { db.update("UPDATE profiles SET is_public=FALSE,updated_at=UTC_TIMESTAMP(6),version=version+1 WHERE user_id=? AND is_public=TRUE",userId); }
    public void saveProfile(long userId, ParticipationController.ProfileRequest value) {

        db.update("""
                INSERT INTO profiles(user_id,nickname,bio,github_url,blog_url,is_public,published_at)
                VALUES (?,?,?,?,?,?,IF(?,UTC_TIMESTAMP(6),NULL))
                ON DUPLICATE KEY UPDATE nickname=VALUES(nickname),bio=VALUES(bio),github_url=VALUES(github_url),blog_url=VALUES(blog_url),
                is_public=VALUES(is_public),published_at=IF(VALUES(is_public),COALESCE(published_at,UTC_TIMESTAMP(6)),published_at),updated_at=UTC_TIMESTAMP(6),version=version+1
                """,userId,value.nickname().strip(),value.bio()==null?null:value.bio().strip(),value.githubUrl(),value.blogUrl(),true,true);
        db.update("DELETE FROM user_interests WHERE user_id=?",userId);
        for (long interest : value.interestIds()) db.update("INSERT INTO user_interests(user_id,interest_id) VALUES (?,?)",userId,interest);
    }
    private static final String MISSIONS = """
            SELECT m.*,p.status,p.unlocked_at,p.completed_at FROM missions m
            LEFT JOIN mission_progress p ON p.mission_id=m.id AND p.user_id=?
            """;
    private Mission mission(ResultSet row,int n) throws SQLException {
        return new Mission(row.getLong("id"),MissionCode.valueOf(row.getString("code")),row.getString("letter"),row.getString("title"),row.getString("hint"),row.getBoolean("active"),
                row.getString("status") == null ? "LOCKED" : row.getString("status"),instant(row,"unlocked_at"),instant(row,"completed_at"));
    }
    public List<Mission> missions(long userId) { return db.query(MISSIONS + " ORDER BY m.sort_order",this::mission,userId); }
    public Mission mission(long userId,MissionCode code) { return db.query(MISSIONS + " WHERE m.code=?",this::mission,userId,code.name()).stream().findFirst().orElseThrow(() -> error(404,"MISSION_NOT_FOUND","미션을 찾을 수 없어요.")); }
    public MissionCode tag(String hash) {
        return db.query("SELECT m.code FROM tags t JOIN missions m ON m.id=t.mission_id WHERE t.token_hash=? AND t.active=TRUE FOR SHARE",(row,n) -> MissionCode.valueOf(row.getString("code")),hash)
                .stream().findFirst().orElseThrow(() -> error(404,"TAG_NOT_FOUND","태그를 확인할 수 없어요. 현장 안내를 확인해 주세요."));
    }
    public TagInspection inspectTag(String hash) {
        return db.query("SELECT m.code,m.letter,m.title FROM tags t JOIN missions m ON m.id=t.mission_id WHERE t.token_hash=? AND t.active=TRUE AND m.active=TRUE",
            (row,n)->new TagInspection(MissionCode.valueOf(row.getString("code")),row.getString("letter"),row.getString("title")),hash)
            .stream().findFirst().orElseThrow(()->error(404,"TAG_NOT_FOUND","태그를 확인할 수 없어요. 현장 안내를 확인해 주세요."));
    }
    public void activeMission(long id) {
        var active = db.queryForObject("SELECT active FROM missions WHERE id=? FOR SHARE",Boolean.class,id);
        if (!Boolean.TRUE.equals(active)) throw error(403,"MISSION_INACTIVE","지금은 사용할 수 없는 미션이에요.");
    }
    public void unlock(long userId,long missionId,Instant received) {
        db.update("INSERT INTO mission_progress(user_id,mission_id,unlocked_at) VALUES (?,?,?)",userId,missionId,Timestamp.from(received));
    }
    public Receipt receipt(long userId,String kind,long sourceId,Instant received) {
        db.update("INSERT INTO submission_receipts(user_id,source_kind,source_id,received_at) VALUES (?,?,?,?)",userId,kind,sourceId,Timestamp.from(received));
        return db.query("SELECT * FROM submission_receipts WHERE user_id=? AND source_kind=? AND source_id=?",this::mapReceipt,userId,kind,sourceId).getFirst();
    }
    private Receipt mapReceipt(ResultSet row,int n) throws SQLException { return new Receipt(row.getLong("id"),row.getLong("user_id"),row.getString("source_kind"),row.getLong("source_id"),instant(row,"received_at")); }
    public Receipt receipt(long id) { return db.query("SELECT * FROM submission_receipts WHERE id=?",this::mapReceipt,id).stream().findFirst().orElseThrow(AuthException::invalid); }
    public Receipt profileReceipt(long userId,long version) {
        return db.query("SELECT * FROM submission_receipts WHERE user_id=? AND source_kind='PROFILE' AND source_id=?",this::mapReceipt,userId,version).stream().findFirst().orElse(null);
    }
    public Completion completion(long id) {
        return db.query("SELECT * FROM event_completions WHERE user_id=?",(row,n) -> new Completion(true,instant(row,"completed_at"),row.getLong("completion_order"),false),id)
                .stream().findFirst().orElse(new Completion(false,null,null,false));
    }
    public void requireModeratedReceipt(Receipt receipt) {
        boolean valid = Boolean.TRUE.equals(db.queryForObject("SELECT (? >= opens_at AND ? < submit_closes_at AND UTC_TIMESTAMP(6)<=moderation_closes_at) FROM event_settings WHERE id=1 FOR SHARE",Boolean.class,Timestamp.from(receipt.receivedAt()),Timestamp.from(receipt.receivedAt())));
        if (!valid) throw error(403,"MODERATION_CLOSED","검수 가능한 기간을 확인해 주세요.");
    }
    public void complete(long userId,Mission mission,Receipt receipt,Instant accepted) {
        db.update("INSERT INTO mission_completion_evidence(user_id,mission_id,source_kind,source_id,receipt_order,accepted_received_at) VALUES (?,?,?,?,?,?)",
                userId,mission.id(),receipt.sourceKind(),receipt.sourceId(),receipt.id(),Timestamp.from(accepted));
        // SHOW ME evidence and completion share the validated DB processing instant.
        // Other missions retain their earlier receipt time and the actual DB completion time.
        Instant completed = mission.code()==MissionCode.SHOW_ME ? accepted : now();
        db.update("UPDATE mission_progress SET status='COMPLETED',completed_at=? WHERE user_id=? AND mission_id=? AND status<>'COMPLETED'",Timestamp.from(completed),userId,mission.id());
        boolean all = missions(userId).size() == MissionCode.values().length && missions(userId).stream().allMatch(m -> "COMPLETED".equals(m.state()));
        if (all && !completion(userId).complete()) {
            long order = db.queryForObject("SELECT next_value FROM event_completion_sequence WHERE id=1 FOR UPDATE",Long.class);
            db.update("INSERT INTO event_completions(user_id,completion_order,completed_at) VALUES (?,?,UTC_TIMESTAMP(6))",userId,order);
            db.update("UPDATE event_completion_sequence SET next_value=next_value+1 WHERE id=1");
        }
    }
}
