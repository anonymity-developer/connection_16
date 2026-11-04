package com.tagme.integration;

import com.tagme.auth.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    static final String ALICE = "a".repeat(26), BOB = "b".repeat(26);
    static final String PASSWORD = "a long test password";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("tagme.auth.enabled", () -> true);
        registry.add("tagme.auth.token", () -> "test-only-token");
        registry.add("tagme.auth.channel-id", () -> "c".repeat(26));
        registry.add("tagme.auth.hmac-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("tagme.auth.resend-seconds", () -> 1);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired TestRestTemplate http;
    @Autowired OtpStore store;
    @Autowired AuthService service;
    @Autowired PasswordEncoder passwords;
    @MockitoBean MattermostClient mattermost;
    @MockitoSpyBean AuthCrypto crypto;
    @MockitoSpyBean AuthRepository accounts;
    @MockitoSpyBean AuthSessions sessions;
    record Sent(String target, OtpStore.Purpose purpose, String code) { }
    final List<Sent> sent = new CopyOnWriteArrayList<>();

    @BeforeEach void cleanDisposableContainers() {
        // These are dedicated, ephemeral Testcontainers, never operator data.
        jdbc.update("DELETE FROM user_credentials"); jdbc.update("DELETE FROM users");
        try (var connection = redis.getConnectionFactory().getConnection()) { connection.serverCommands().flushDb(); }
        reset(mattermost, crypto, accounts, sessions); sent.clear();
        when(mattermost.byUsername(anyString())).thenReturn(Optional.empty());
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity(ALICE, "alice")));
        when(mattermost.byUsername("bob")).thenReturn(Optional.of(new MattermostClient.Identity(BOB, "bob")));
        when(mattermost.byId(ALICE)).thenReturn(Optional.of(new MattermostClient.Identity(ALICE, "alice")));
        when(mattermost.byId(BOB)).thenReturn(Optional.of(new MattermostClient.Identity(BOB, "bob")));
        doAnswer(invocation -> { sent.add(new Sent(((MattermostClient.Identity)invocation.getArgument(0)).id(), invocation.getArgument(1), invocation.getArgument(2))); return null; })
                .when(mattermost).sendCode(any(), any(), anyString());
    }
    class Browser {
        final Map<String, String> cookies = new HashMap<>();
        ResponseEntity<Map> raw(HttpMethod method, String path, Object body, String csrf) {
            var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            if (!cookies.isEmpty()) headers.add(HttpHeaders.COOKIE, String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
            if (csrf != null) headers.add("X-XSRF-TOKEN", csrf);
            var response = http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), Map.class);
            for (String cookie : response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)) {
                String[] pair = cookie.split(";", 2)[0].split("=", 2);
                if (pair.length == 2) { if (pair[1].isEmpty()) cookies.remove(pair[0]); else cookies.put(pair[0], pair[1]); }
            }
            return response;
        }
        ResponseEntity<Map> get(String path) { return raw(HttpMethod.GET, path, null, null); }
        ResponseEntity<Map> send(HttpMethod method, String path, Object body) {
            var token = get("/csrf"); assertThat(token.getStatusCode().value()).isEqualTo(200);
            return raw(method, path, body, (String) token.getBody().get("token"));
        }
        String otp(String username, OtpStore.Purpose purpose) {
            Map<String, Object> body = new HashMap<>(); body.put("purpose", purpose.name()); if (username != null) body.put("username", username);
            var response = send(HttpMethod.POST, "/auth/otp/request", body);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            return (String) response.getBody().get("challengeId");
        }
        ResponseEntity<Map> login(String username, String password) { return send(HttpMethod.POST, "/auth/login", Map.of("username", username, "password", password)); }
    }
    void allowResend() {
        for (String pattern : List.of("tagme:auth:cooldown:*", "tagme:auth:limit:otp-resend:*")) {
            var keys = redis.keys(pattern); if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
    }
    Browser signup(String name) {
        var browser = new Browser(); String id = browser.otp(name, OtpStore.Purpose.SIGN_UP);
        var response = browser.send(HttpMethod.POST, "/auth/signup", Map.of("challengeId", id, "code", sent.getLast().code(), "password", PASSWORD));
        assertThat(response.getStatusCode().value()).isEqualTo(201); return browser;
    }
    @Test void signupPreservesLeadingZeroStoresOnlyHashAndRotatesLoginSession() {
        doReturn("012345").when(crypto).code();
        var browser = signup("alice");
        var account = accounts.byExternalId(ALICE).orElseThrow();
        assertThat(passwords.matches(PASSWORD, account.hash())).isTrue();
        assertThat(account.hash()).doesNotContain(PASSWORD).startsWith("{pbkdf2-sha256-v1}");
        assertThat(account.version()).isEqualTo(1L);
        var me = browser.get("/users/me");
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody()).containsEntry("username", "alice").containsEntry("onboardingRequired", true);
        assertThat(me.getBody().toString()).doesNotContain(ALICE, account.hash(), "012345", PASSWORD);
        assertThat(browser.get("/admin/users").getStatusCode().value()).isEqualTo(403);
        String old = browser.cookies.get("TAGME_SESSION");
        assertThat(browser.login("alice", PASSWORD).getStatusCode().value()).isEqualTo(200);
        assertThat(browser.cookies.get("TAGME_SESSION")).isNotEqualTo(old);
        assertThat(redis.keys("tagme:auth:challenge:*")).isEmpty();
    }
    @Test void passwordChangeRequiresSameSessionPurposeAndRevokesBothSessionsEvenWithoutRedisCleanup() {
        var first = signup("alice"); var second = new Browser();
        assertThat(second.login("alice", PASSWORD).getStatusCode().value()).isEqualTo(200);
        assertThat(first.send(HttpMethod.POST, "/auth/otp/request", Map.of("purpose", "PASSWORD_CHANGE", "username", "bob")).getStatusCode().value()).isEqualTo(400);
        allowResend();
        String challenge = first.otp(null, OtpStore.Purpose.PASSWORD_CHANGE); String code = sent.getLast().code();
        var payload = Map.of("challengeId", challenge, "code", code, "newPassword", "the replacement password");
        assertThat(second.send(HttpMethod.PUT, "/users/me/password", payload).getStatusCode().value()).isEqualTo(401);
        assertThat(first.send(HttpMethod.POST, "/auth/password/reset", payload).getStatusCode().value()).isEqualTo(401);
        var foreignTarget = new HashMap<String, Object>(payload); foreignTarget.put("userId", 999);
        assertThat(first.send(HttpMethod.PUT, "/users/me/password", foreignTarget).getStatusCode().value()).isEqualTo(400);
        doNothing().when(sessions).revokeOld(any()); // missed Redis deletion must not authorize a stale version
        assertThat(first.send(HttpMethod.PUT, "/users/me/password", payload).getStatusCode().value()).isEqualTo(204);
        assertThat(first.get("/users/me").getStatusCode().value()).isEqualTo(401);
        var stale = second.get("/users/me");
        assertThat(stale.getStatusCode().value()).isEqualTo(401);
        assertThat(stale.getBody()).containsEntry("code", "SESSION_EXPIRED");
        var fresh = new Browser();
        assertThat(fresh.login("alice", PASSWORD).getStatusCode().value()).isEqualTo(401);
        assertThat(fresh.login("alice", "the replacement password").getStatusCode().value()).isEqualTo(200);
    }
    @Test void resetRequiresDmDoesNotCreateAccountsOrAutoLoginAndRejectsReplay() {
        var signedIn = signup("alice"); var resetter = new Browser(); allowResend();
        String challenge = resetter.otp("alice", OtpStore.Purpose.PASSWORD_RESET);
        var payload = Map.of("challengeId", challenge, "code", sent.getLast().code(), "newPassword", "a new reset password");
        assertThat(resetter.send(HttpMethod.POST, "/auth/password/reset", payload).getStatusCode().value()).isEqualTo(204);
        assertThat(resetter.get("/users/me").getStatusCode().value()).isEqualTo(401);
        assertThat(signedIn.get("/users/me").getStatusCode().value()).isEqualTo(401);
        assertThat(resetter.send(HttpMethod.POST, "/auth/password/reset", payload).getStatusCode().value()).isEqualTo(401);
        assertThat(new Browser().login("alice", "a new reset password").getStatusCode().value()).isEqualTo(200);
        int messages = sent.size();
        String dummy = resetter.otp("nobody", OtpStore.Purpose.PASSWORD_RESET);
        assertThat(sent).hasSize(messages);
        assertThat(resetter.send(HttpMethod.POST, "/auth/password/reset", Map.of("challengeId", dummy, "code", "012345", "newPassword", PASSWORD)).getStatusCode().value()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
    }
    @Test void renamedUserKeepsExternalIdentityAndReusedUsernameCannotInheritPassword() {
        signup("alice");
        when(mattermost.byUsername("new-alice")).thenReturn(Optional.of(new MattermostClient.Identity(ALICE, "new-alice")));
        assertThat(new Browser().login("new-alice", PASSWORD).getStatusCode().value()).isEqualTo(200);
        when(mattermost.byUsername("alice")).thenReturn(Optional.of(new MattermostClient.Identity(BOB, "alice")));
        assertThat(new Browser().login("alice", PASSWORD).getStatusCode().value()).isEqualTo(401);
        assertThat(accounts.byExternalId(ALICE).orElseThrow().username()).isEqualTo("new-alice");
    }
    @Test void legacyRowGetsInitialPasswordOnlyAfterDmAndExistingHashCannotBeOverwritten() {
        jdbc.update("INSERT INTO users (mattermost_id, real_name, region, class_no) VALUES (?,?,?,?)", ALICE, "legacy", "서울", 1);
        var browser = signup("alice"); var hash = accounts.byExternalId(ALICE).orElseThrow().hash(); allowResend(); int messages = sent.size();
        String dummy = browser.otp("alice", OtpStore.Purpose.SIGN_UP);
        assertThat(sent).hasSize(messages);
        assertThat(browser.send(HttpMethod.POST, "/auth/signup", Map.of("challengeId", dummy, "code", "012345", "password", "another long password")).getStatusCode().value()).isEqualTo(401);
        assertThat(accounts.byExternalId(ALICE).orElseThrow().hash()).isEqualTo(hash);
    }
    @Test void suspendedAccountCannotSetInitialPasswordOrKeepAnOldSession() {
        jdbc.update("INSERT INTO users (mattermost_id) VALUES (?)", ALICE);
        var browser = new Browser(); String id = browser.otp("alice", OtpStore.Purpose.SIGN_UP); String code = sent.getLast().code();
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE mattermost_id=?", ALICE);
        assertThat(browser.send(HttpMethod.POST, "/auth/signup", Map.of("challengeId", id, "code", code, "password", PASSWORD)).getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class)).isZero();
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE mattermost_id=?", ALICE); allowResend();
        var signedIn = signup("alice");
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE mattermost_id=?", ALICE);
        assertThat(signedIn.get("/users/me").getStatusCode().value()).isEqualTo(401);
        assertThat(new Browser().login("alice", PASSWORD).getStatusCode().value()).isEqualTo(401);
    }
    @Test void signupDbFailureRollsBackUserButNeverReactivatesConsumedOtp() {
        var browser = new Browser(); String id = browser.otp("alice", OtpStore.Purpose.SIGN_UP);
        var payload = Map.of("challengeId", id, "code", sent.getLast().code(), "password", PASSWORD);
        doThrow(new DataAccessResourceFailureException("test failure")).when(accounts).insertCredential(anyLong(), anyString());
        var response = browser.send(HttpMethod.POST, "/auth/signup", payload);
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsEntry("code", "AUTH_RESTART_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(browser.send(HttpMethod.POST, "/auth/signup", payload).getStatusCode().value()).isEqualTo(401);
    }
    @Test void csrfAndAnonymousPasswordChangeCannotBeBypassed() {
        var browser = new Browser();
        assertThat(browser.raw(HttpMethod.POST, "/auth/login", Map.of("username", "alice", "password", PASSWORD), null).getStatusCode().value()).isEqualTo(403);
        assertThat(browser.send(HttpMethod.POST, "/auth/otp/request", Map.of("purpose", "PASSWORD_CHANGE")).getStatusCode().value()).isEqualTo(401);
        assertThat(browser.send(HttpMethod.POST, "/auth/signup", Map.of("challengeId", crypto.identifier(), "code", "１２３４５６", "password", PASSWORD)).getStatusCode().value()).isEqualTo(400);
    }
    @Test void otpProofIsHashedPurposeBoundLimitedExpiringAndConsumedAtomically() throws Exception {
        String id = crypto.identifier(); var challenge = new OtpStore.Challenge(OtpStore.Purpose.SIGN_UP, ALICE, 0, 0, "");
        store.issue(id, challenge, "012345");
        assertThat(redis.opsForHash().entries("tagme:auth:challenge:" + id).values()).doesNotContain("012345");
        assertThatThrownBy(() -> store.peek(id, OtpStore.Purpose.PASSWORD_RESET, "")).isInstanceOf(AuthException.class);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> { try { store.consume(id, challenge, "012345"); return true; } catch (AuthException rejected) { return false; } }));
            int success = 0; for (var future : futures) if (future.get()) success++;
            assertThat(success).isEqualTo(1);
        }
        allowResend(); String wrong = crypto.identifier(); store.issue(wrong, challenge, "012345");
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> store.consume(wrong, challenge, "999999")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> store.consume(wrong, challenge, "012345")).isInstanceOf(AuthException.class);
        allowResend(); String expired = crypto.identifier(); store.issue(expired, challenge, "012345");
        redis.expire("tagme:auth:challenge:" + expired, Duration.ZERO);
        assertThatThrownBy(() -> store.peek(expired, OtpStore.Purpose.SIGN_UP, "")).isInstanceOf(AuthException.class);
    }
    @Test void resendInvalidatesOldProofAndAccountCooldownSpansPurposes() {
        var challenge = new OtpStore.Challenge(OtpStore.Purpose.SIGN_UP, ALICE, 0, 0, "");
        String first = crypto.identifier(); store.issue(first, challenge, "012345");
        var other = new OtpStore.Challenge(OtpStore.Purpose.PASSWORD_RESET, ALICE, 0, 0, "");
        assertThatThrownBy(() -> store.issue(crypto.identifier(), other, "123456")).isInstanceOf(AuthException.class);
        allowResend(); String second = crypto.identifier(); store.issue(second, challenge, "123456");
        assertThatThrownBy(() -> store.consume(first, challenge, "012345")).isInstanceOf(AuthException.class);
        store.consume(second, challenge, "123456");
    }
    @Test void competingPasswordOperationsCannotReuseTheOldCredentialVersion() throws Exception {
        signup("alice"); var account = accounts.byExternalId(ALICE).orElseThrow();
        var reset = new OtpStore.Challenge(OtpStore.Purpose.PASSWORD_RESET, ALICE, account.id(), 1, "");
        var change = new OtpStore.Challenge(OtpStore.Purpose.PASSWORD_CHANGE, ALICE, account.id(), 1, service.sessionBinding("test-session"));
        String first = crypto.identifier(), second = crypto.identifier(); allowResend(); store.issue(first, reset, "012345"); allowResend(); store.issue(second, change, "123456");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = pool.submit(() -> { try { service.change(first, "012345", "first long password", OtpStore.Purpose.PASSWORD_RESET, null, null, "test-ip"); return true; } catch (AuthException rejected) { return false; } });
            var two = pool.submit(() -> { try { service.change(second, "123456", "second long password", OtpStore.Purpose.PASSWORD_CHANGE, account.principal(), "test-session", "test-ip"); return true; } catch (AuthException rejected) { return false; } });
            assertThat((one.get() ? 1 : 0) + (two.get() ? 1 : 0)).isEqualTo(1);
        }
        assertThat(accounts.byExternalId(ALICE).orElseThrow().version()).isEqualTo(2L);
    }
    @Test void sendFailureDeletesChallengeAndExternalFailureNeverGrantsSession() {
        doThrow(AuthException.unavailable()).when(mattermost).sendCode(any(), any(), anyString());
        var browser = new Browser();
        assertThat(browser.send(HttpMethod.POST, "/auth/otp/request", Map.of("username", "alice", "purpose", "SIGN_UP")).getStatusCode().value()).isEqualTo(503);
        assertThat(redis.keys("tagme:auth:challenge:*")).isEmpty();
        when(mattermost.byUsername("alice")).thenThrow(AuthException.unavailable());
        assertThat(browser.login("alice", PASSWORD).getStatusCode().value()).isEqualTo(503);
        assertThat(browser.get("/users/me").getStatusCode().value()).isEqualTo(401);
    }
}
