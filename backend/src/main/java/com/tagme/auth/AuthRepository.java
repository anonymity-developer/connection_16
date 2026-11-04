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
            SELECT u.*, c.password_hash, c.credential_version, p.nickname,
                (p.user_id IS NULL OR u.real_name IS NULL OR u.region IS NULL OR u.class_no IS NULL) AS needs_onboarding
            FROM users u LEFT JOIN user_credentials c ON c.user_id=u.id LEFT JOIN profiles p ON p.user_id=u.id
            """;
    private Account account(ResultSet row, int ignored) throws SQLException {
        return new Account(row.getLong("id"), row.getString("mattermost_id"), row.getString("mattermost_username"),
                row.getString("status"), row.getString("role"), row.getString("password_hash"),
                row.getObject("credential_version", Long.class), row.getString("nickname"), row.getBoolean("needs_onboarding"));
    }
    public Optional<Account> byExternalId(String id) { return jdbc.query(SELECT + " WHERE u.mattermost_id=?", this::account, id).stream().findFirst(); }
    public Optional<Account> byId(long id) { return jdbc.query(SELECT + " WHERE u.id=?", this::account, id).stream().findFirst(); }
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
    public void recordLogin(long id) { jdbc.update("UPDATE users SET last_login_at=UTC_TIMESTAMP(6) WHERE id=?", id); }
    public void changePassword(long id, String hash) { jdbc.update("UPDATE user_credentials SET password_hash=?,password_updated_at=UTC_TIMESTAMP(6),credential_version=credential_version+1 WHERE user_id=?", hash, id); }
}
