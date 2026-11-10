package com.tagme.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;
    public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Account(long id, String mattermostId, String username, String status, String role,
                          String hash, Long version, String nickname, boolean onboardingRequired) {
        public AuthPrincipal principal() { return new AuthPrincipal(id, version, role); }
        @Override public String toString() { return "Account[id=" + id + "]"; }
    }
    private static final String SELECT = """
            SELECT u.*, COALESCE(la.login_id,u.mattermost_username) AS login_name, c.password_hash, c.credential_version, p.nickname,
                (la.user_id IS NULL AND (p.user_id IS NULL OR u.real_name IS NULL OR u.region IS NULL OR u.class_no IS NULL
                 OR TRIM(COALESCE(p.nickname,''))='' OR TRIM(COALESCE(u.real_name,''))=''
                 OR TRIM(COALESCE(u.region,''))='' OR u.class_no<1 OR u.mattermost_profile_synced_at IS NULL OR p.is_public=FALSE
                 OR (SELECT COUNT(*) FROM current_consent_policies) <> 2
                 OR EXISTS (SELECT 1 FROM current_consent_policies cp WHERE NOT EXISTS (
                    SELECT 1 FROM user_consents uc WHERE uc.user_id=u.id AND uc.policy_type=cp.policy_type
                    AND uc.policy_version=cp.policy_version AND uc.withdrawn_at IS NULL)))) AS needs_onboarding
            FROM users u LEFT JOIN user_credentials c ON c.user_id=u.id LEFT JOIN profiles p ON p.user_id=u.id
            LEFT JOIN local_admin_accounts la ON la.user_id=u.id
            """;
    private Account account(ResultSet row, int ignored) throws SQLException {
        return new Account(row.getLong("id"), row.getString("mattermost_id"), row.getString("login_name"),
                row.getString("status"), row.getString("role"), row.getString("password_hash"),
                row.getObject("credential_version", Long.class), row.getString("nickname"), row.getBoolean("needs_onboarding"));
    }
    public Optional<Account> byExternalId(String id) { return jdbc.query(SELECT + " WHERE u.mattermost_id=?", this::account, id).stream().findFirst(); }
    public Optional<Account> byId(long id) { return jdbc.query(SELECT + " WHERE u.id=?", this::account, id).stream().findFirst(); }
    public Optional<Account> byAdminLogin(String login) { return jdbc.query(SELECT + " WHERE la.login_id=?",this::account,login).stream().findFirst(); }
    public boolean localAdminAvailable() { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM local_admin_accounts a JOIN users u ON u.id=a.user_id JOIN user_credentials c ON c.user_id=u.id WHERE u.status='ACTIVE' AND u.role='ADMIN')",Boolean.class)); }
    public void lockUser(long id) { jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", id); }
    public long insertUser(String externalId, String username) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO users (mattermost_id,mattermost_username) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, externalId); statement.setString(2, username); return statement;
        }, key);
        return key.getKey().longValue();
    }
    public void insertCredential(long id, String hash) { jdbc.update("INSERT INTO user_credentials (user_id,password_hash,password_updated_at) VALUES (?,?,UTC_TIMESTAMP(6))", id, hash); }
    public void updateUsername(long id, String username) { jdbc.update("UPDATE users SET mattermost_username=? WHERE id=?", username, id); }
    public void syncProfile(long id,MattermostProfile profile) {
        if(profile==null)throw new AuthException(409,"MATTERMOST_PROFILE_INVALID","Mattermost 이름을 이름[지역_반] 형식으로 확인한 뒤 다시 인증해 주세요.");
        // Lock users before calling this; invalidate in-flight profile edits when imported data changes.
        Boolean changed=jdbc.queryForObject("SELECT NOT(real_name<=>?) OR NOT(region<=>?) OR NOT(class_no<=>?) OR mattermost_picture_updated_at<>? FROM users WHERE id=?",Boolean.class,profile.realName(),profile.region(),profile.classNo(),profile.pictureUpdatedAt(),id);
        jdbc.update("UPDATE users SET real_name=?,region=?,class_no=?,mattermost_picture_updated_at=?,mattermost_profile_synced_at=UTC_TIMESTAMP(6) WHERE id=?",profile.realName(),profile.region(),profile.classNo(),profile.pictureUpdatedAt(),id);
        if(Boolean.TRUE.equals(changed))jdbc.update("UPDATE profiles SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=?",id);
    }
    public record ProfileIdentity(String realName,String region,Integer classNo,long pictureVersion,boolean ready) { }
    public ProfileIdentity profileIdentity(long id) { return jdbc.queryForObject("SELECT real_name,region,class_no,mattermost_picture_updated_at,mattermost_profile_synced_at FROM users WHERE id=?",(r,n)->new ProfileIdentity(r.getString("real_name"),r.getString("region"),r.getObject("class_no",Integer.class),r.getLong("mattermost_picture_updated_at"),r.getTimestamp("mattermost_profile_synced_at")!=null),id); }
    public void recordLogin(long id) { jdbc.update("UPDATE users SET last_login_at=UTC_TIMESTAMP(6) WHERE id=?", id); }
    public void changePassword(long id, String hash) { jdbc.update("UPDATE user_credentials SET password_hash=?,password_updated_at=UTC_TIMESTAMP(6),credential_version=credential_version+1 WHERE user_id=?", hash, id); }
}
