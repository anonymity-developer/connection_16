package com.tagme.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AuthCrypto {
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();
    public AuthCrypto(AuthProperties properties) { this.properties = properties; }
    public String identifier() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String code() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public String digest(String value) {
        // Administrator logins also use the shared limiter when Mattermost auth is disabled.
        if (properties.hmacKey().isBlank()) throw AuthException.unavailable();
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(properties.hmacKey()), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("HMAC is unavailable"); }
    }
}
