package com.tagme.auth;

import java.net.URI;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("tagme.auth")
public record AuthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://meeting.ssafy.com") URI baseUrl,
        @DefaultValue("") String token,
        @DefaultValue("") String loginId,
        @DefaultValue("") String loginPassword,
        @DefaultValue("") String channelId,
        @DefaultValue("") String teamName,
        @DefaultValue("") String channelName,
        @DefaultValue("") String hmacKey,
        @DefaultValue("180") int otpTtlSeconds,
        @DefaultValue("60") int resendSeconds,
        @DefaultValue("5") int otpMaxAttempts,
        @DefaultValue("5") int otpAccountLimit,
        @DefaultValue("10") int loginAccountLimit,
        @DefaultValue("120") int ipPerMinute) {
    // Preserve the original constructor used by existing tests/internals.
    public AuthProperties(boolean enabled, URI baseUrl, String token, String channelId,
            String hmacKey, int otpTtlSeconds, int resendSeconds, int otpMaxAttempts,
            int otpAccountLimit, int loginAccountLimit, int ipPerMinute) {
        this(enabled, baseUrl, token, "", "", channelId, "", "", hmacKey, otpTtlSeconds,
                resendSeconds, otpMaxAttempts, otpAccountLimit, loginAccountLimit, ipPerMinute);
    }
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AuthProperties {
        if (otpTtlSeconds < 60 || otpTtlSeconds > 600 || resendSeconds < 1 || resendSeconds > otpTtlSeconds
                || otpMaxAttempts < 1 || otpMaxAttempts > 10 || otpAccountLimit < 1
                || loginAccountLimit < 1 || ipPerMinute < 1) throw new IllegalArgumentException("Invalid authentication limits");
        if (enabled) {
            // Reject partial/mixed strategies rather than silently ignoring credentials.
            boolean hasPat = !token.isBlank();
            boolean hasLoginId = !loginId.isBlank(), hasPassword = !loginPassword.isBlank();
            boolean hasChannelId = !channelId.isBlank();
            boolean hasTeam = !teamName.isBlank(), hasChannelName = !channelName.isBlank();
            if (hasLoginId != hasPassword || hasPat == hasLoginId
                    || hasTeam != hasChannelName || hasChannelId == hasTeam
                    || (hasChannelId && !channelId.matches("[a-z0-9]{26}"))
                    || (hasTeam && (!teamName.matches("[a-z0-9_-]{1,64}") || !channelName.matches("[a-z0-9_-]{1,64}"))))
                throw new IllegalArgumentException("Configure one complete sender strategy and either channel ID or team/channel names");
            try {
                if (Base64.getDecoder().decode(hmacKey).length < 32) throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("AUTH_HMAC_KEY must contain at least 32 random bytes encoded as Base64");
            }
            boolean local = "http".equals(baseUrl.getScheme()) && ("127.0.0.1".equals(baseUrl.getHost()) || "localhost".equals(baseUrl.getHost()));
            if ((!"https".equals(baseUrl.getScheme()) && !local) || baseUrl.getUserInfo() != null
                    || baseUrl.getQuery() != null || baseUrl.getFragment() != null || baseUrl.getHost() == null) {
                throw new IllegalArgumentException("Mattermost requires HTTPS (HTTP is allowed only for local testing)");
            }
        }
    }
    @Override public String toString() { return "AuthProperties[redacted]"; }
}
