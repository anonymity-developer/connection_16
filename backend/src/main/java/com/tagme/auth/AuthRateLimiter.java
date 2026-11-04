package com.tagme.auth;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            if count > tonumber(ARGV[1]) then return math.max(1, redis.call('TTL', KEYS[1])) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    public AuthRateLimiter(StringRedisTemplate redis, AuthCrypto crypto) { this.redis = redis; this.crypto = crypto; }
    public void check(String scope, String subject, int limit, int seconds) {
        Long wait = redis.execute(LIMIT, List.of("tagme:auth:limit:" + scope + ":" + crypto.digest(subject)), Integer.toString(limit), Integer.toString(seconds));
        if (wait == null) throw AuthException.unavailable();
        if (wait > 0) throw AuthException.limited(wait.intValue());
    }
}
