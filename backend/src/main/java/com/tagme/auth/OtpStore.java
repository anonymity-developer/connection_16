package com.tagme.auth;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class OtpStore {
    public enum Purpose {
        SIGN_UP("회원가입"), PASSWORD_CHANGE("비밀번호 변경"), PASSWORD_RESET("비밀번호 재설정");
        private final String label;
        Purpose(String label) { this.label = label; }
        public String label() { return label; }
    }
    public record Challenge(Purpose purpose, String externalId, long userId, long version, String binding) { }
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    private final AuthProperties properties;
    private final DefaultRedisScript<Long> issue = script("auth/issue.lua");
    private final DefaultRedisScript<Long> consume = script("auth/consume.lua");
    private static DefaultRedisScript<Long> script(String path) {
        var script = new DefaultRedisScript<Long>(); script.setLocation(new ClassPathResource(path)); script.setResultType(Long.class); return script;
    }
    public OtpStore(StringRedisTemplate redis, AuthCrypto crypto, AuthProperties properties) { this.redis = redis; this.crypto = crypto; this.properties = properties; }
    private String key(String id) { return "tagme:auth:challenge:" + id; }
    private String proof(String id, Purpose purpose, String externalId, String code) { return crypto.digest(id + "|" + purpose.name() + "|" + externalId + "|" + code); }
    public void issue(String id, Challenge challenge, String code) {
        String account = crypto.digest("account:" + challenge.externalId());
        Long wait = redis.execute(issue, List.of("tagme:auth:cooldown:" + account,
                        "tagme:auth:active:" + account + ":" + challenge.purpose(), key(id)),
                challenge.purpose().name(), challenge.externalId(), Long.toString(challenge.userId()), Long.toString(challenge.version()),
                challenge.binding(), proof(id, challenge.purpose(), challenge.externalId(), code),
                Integer.toString(properties.otpTtlSeconds()), Integer.toString(properties.resendSeconds()));
        if (wait == null) throw AuthException.unavailable();
        if (wait > 0) throw AuthException.limited(wait.intValue());
    }
    public Challenge peek(String id, Purpose expected, String binding) {
        var values = redis.opsForHash().entries(key(id));
        if (values.isEmpty() || !expected.name().equals(values.get("purpose")) || !binding.equals(values.get("binding"))) throw AuthException.invalid();
        return new Challenge(expected, (String) values.get("externalId"), Long.parseLong((String) values.get("userId")), Long.parseLong((String) values.get("version")), binding);
    }
    public void consume(String id, Challenge challenge, String code) {
        Long success = redis.execute(consume, List.of(key(id)), challenge.purpose().name(), challenge.binding(),
                proof(id, challenge.purpose(), challenge.externalId(), code), Integer.toString(properties.otpMaxAttempts()));
        if (!Long.valueOf(1).equals(success)) throw AuthException.invalid();
    }
    public int remainingSeconds(String id) {
        Long milliseconds = redis.getExpire(key(id), java.util.concurrent.TimeUnit.MILLISECONDS);
        return milliseconds == null || milliseconds <= 0 ? 0 : (int) (milliseconds / 1000);
    }
    public void invalidate(String id) { redis.delete(key(id)); }
}
