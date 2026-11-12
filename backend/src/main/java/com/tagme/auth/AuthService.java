package com.tagme.auth;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    public record OtpResponse(String challengeId, int expiresIn, int resendAfter) { }
    private final AuthProperties properties;
    private final AuthRepository accounts;
    private final MattermostClient mattermost;
    private final OtpStore challenges;
    private final AuthCrypto crypto;
    private final AuthRateLimiter limits;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final String dummyHash;
    public AuthService(AuthProperties properties, AuthRepository accounts, MattermostClient mattermost, OtpStore challenges,
                       AuthCrypto crypto, AuthRateLimiter limits, PasswordEncoder passwords, org.springframework.transaction.PlatformTransactionManager manager) {
        this.properties = properties; this.accounts = accounts; this.mattermost = mattermost; this.challenges = challenges;
        this.crypto = crypto; this.limits = limits; this.passwords = passwords; this.transactions = new TransactionTemplate(manager);
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    private void ready() { if (!properties.enabled()) throw AuthException.unavailable(); }
    private String username(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9._-]{1,64}")) throw new AuthException(400, "INVALID_USERNAME", "Mattermost 아이디를 확인해 주세요.");
        return name;
    }
    private boolean active(AuthRepository.Account account) { return account != null && "ACTIVE".equals(account.status()); }
    private long version(AuthRepository.Account account) { return account == null || account.version() == null ? 0 : account.version(); }
    public String sessionBinding(String sessionId) { return crypto.digest("session:" + sessionId); }
    public OtpResponse request(OtpStore.Purpose purpose, String input, AuthPrincipal principal, String sessionId, String ip) {
        ready();
        String name;
        AuthRepository.Account current = null;
        if (purpose == OtpStore.Purpose.PASSWORD_CHANGE) {
            if (principal == null || sessionId == null) throw new AuthException(401, "UNAUTHENTICATED", "로그인이 필요합니다.");
            if (input != null) throw new AuthException(400, "INVALID_INPUT", "변경 대상은 로그인한 본인 계정입니다.");
            current = accounts.byId(principal.userId()).filter(a -> active(a) && Objects.equals(a.version(), principal.version())).orElseThrow(AuthException::invalid);
            name = current.username();
        } else name = username(input);
        limits.check("otp-ip", ip, properties.ipPerMinute(), 60);
        // Applied before lookup, including nonexistent/ineligible accounts.
        limits.check("otp-name", name, properties.otpAccountLimit(), 900);
        limits.check("otp-resend", name, 1, properties.resendSeconds());
        var identity = current == null ? mattermost.byUsername(name) : mattermost.byId(current.mattermostId());
        String id = crypto.identifier();
        var response = new OtpResponse(id, properties.otpTtlSeconds(), properties.resendSeconds());
        if (identity.isEmpty()) return response;
        var target = identity.get();
        limits.check("otp-account", target.id(), properties.otpAccountLimit(), 900);
        var account = accounts.byExternalId(target.id()).orElse(null);
        // Explicit signup UX: report registered participants before issuing a challenge.
        // Login/reset retain their generic responses; existing request limits still apply.
        if (purpose == OtpStore.Purpose.SIGN_UP && active(account) && account.hash() != null) throw AuthException.alreadyRegistered();
        boolean eligible = purpose == OtpStore.Purpose.SIGN_UP ? account == null || active(account) && account.hash() == null
                : active(account) && account.hash() != null;
        if (!eligible) return response;
        var challenge = new OtpStore.Challenge(purpose, target.id(), account == null ? 0 : account.id(), version(account),
                purpose == OtpStore.Purpose.PASSWORD_CHANGE ? sessionBinding(sessionId) : "");
        String code = crypto.code();
        challenges.issue(id, challenge, code);
        try { mattermost.sendCode(target, purpose, code); }
        catch (RuntimeException failure) { challenges.invalidate(id); throw failure; }
        int remaining = challenges.remainingSeconds(id);
        if (remaining <= 0) { challenges.invalidate(id); throw restart(); }
        return new OtpResponse(id, remaining, properties.resendSeconds());
    }
    public AuthRepository.Account login(String input, String password, String ip) {
        ready();
        String name = username(input);
        limits.check("login-ip", ip, properties.ipPerMinute(), 60);
        limits.check("login-name", name, properties.loginAccountLimit(), 900);
        var identity = mattermost.byUsername(name);
        var account = identity.flatMap(i -> accounts.byExternalId(i.id())).orElse(null);
        String hash = account != null && account.hash() != null ? account.hash() : dummyHash;
        boolean matches;
        try { matches = passwords.matches(password, hash); }
        catch (IllegalArgumentException invalidStoredHash) { passwords.matches(password, dummyHash); matches = false; }
        if (!matches || !active(account) || account.hash() == null || identity.isEmpty()) throw AuthException.loginFailed();
        try {
            return transactions.execute(status -> {
                accounts.lockUser(account.id());
                var locked = accounts.byId(account.id()).orElseThrow(AuthException::loginFailed);
                if (!active(locked) || !Objects.equals(locked.version(), account.version())) throw AuthException.loginFailed();
                accounts.updateUsername(account.id(), identity.get().username());
                accounts.syncProfile(account.id(),identity.get().profile());
                accounts.recordLogin(account.id());
                return accounts.byId(account.id()).orElseThrow(AuthException::loginFailed);
            });
        } catch (DataIntegrityViolationException collision) { throw AuthException.conflict(); }
    }
    public AuthRepository.Account signup(String id, String code, String password, String ip) {
        PasswordPolicy.validate(password);
        var verified = verify(id, code, OtpStore.Purpose.SIGN_UP, null, null, ip);
        String hash = passwords.encode(password); // expensive work outside DB row locks
        try {
            return transactions.execute(status -> {
                long userId = verified.challenge().userId();
                if (userId == 0) userId = accounts.insertUser(verified.identity().id(), verified.identity().username());
                else {
                    // This locking read must precede any consistent read in
                    // the transaction, including the legacy first-password path.
                    accounts.lockUser(userId);
                    var locked = accounts.byId(userId).orElseThrow(AuthException::invalid);
                    if (!active(locked) || !locked.mattermostId().equals(verified.identity().id())) throw AuthException.conflict();
                    if (locked.hash() != null) throw AuthException.alreadyRegistered();
                    accounts.updateUsername(userId, verified.identity().username());
                }
                accounts.syncProfile(userId,verified.identity().profile());
                accounts.insertCredential(userId, hash);
                accounts.recordLogin(userId);
                return accounts.byId(userId).orElseThrow(AuthException::invalid);
            });
        } catch (DataIntegrityViolationException collision) { throw AuthException.conflict(); }
        catch (DataAccessException failure) { throw restart(); }
    }
    public AuthRepository.Account change(String id, String code, String password, OtpStore.Purpose purpose,
                                         AuthPrincipal principal, String sessionId, String ip) {
        PasswordPolicy.validate(password);
        var verified = verify(id, code, purpose, principal, sessionId, ip);
        String hash = passwords.encode(password);
        try {
            return transactions.execute(status -> {
                long userId = verified.challenge().userId();
                accounts.lockUser(userId);
                var locked = accounts.byId(userId).orElseThrow(AuthException::invalid);
                if (!active(locked) || locked.hash() == null || !locked.mattermostId().equals(verified.identity().id())
                        || version(locked) != verified.challenge().version()) throw AuthException.invalid();
                accounts.updateUsername(userId, verified.identity().username());
                accounts.syncProfile(userId,verified.identity().profile());
                accounts.changePassword(userId, hash);
                return accounts.byId(userId).orElseThrow(AuthException::invalid);
            });
        } catch (DataIntegrityViolationException collision) { throw AuthException.conflict(); }
        catch (DataAccessException failure) { throw restart(); }
    }
    private record Verified(OtpStore.Challenge challenge, MattermostClient.Identity identity) { }
    private Verified verify(String id, String code, OtpStore.Purpose purpose, AuthPrincipal principal, String sessionId, String ip) {
        ready();
        limits.check("verify-ip", ip, properties.ipPerMinute(), 60);
        String binding = "";
        if (purpose == OtpStore.Purpose.PASSWORD_CHANGE) {
            if (principal == null || sessionId == null) throw AuthException.invalid();
            binding = sessionBinding(sessionId);
        }
        var challenge = challenges.peek(id, purpose, binding);
        if (purpose == OtpStore.Purpose.PASSWORD_CHANGE
                && (principal.userId() != challenge.userId() || principal.version() != challenge.version())) throw AuthException.invalid();
        // Reject wrong codes atomically before making external calls. A valid
        // proof remains one-time even if revalidation or the DB later fails.
        challenges.consume(id, challenge, code);
        try {
            var identity = mattermost.byId(challenge.externalId()).orElseThrow(AuthException::invalid);
            return new Verified(challenge, identity);
        } catch (AuthException failure) {
            if (failure.status() == 503) throw restart();
            throw failure;
        }
    }
    private AuthException restart() { return new AuthException(503, "AUTH_RESTART_REQUIRED", "처리하지 못했어요. 인증번호를 새로 받고 다시 시도해 주세요."); }
}
