package com.tagme.auth;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthBeans {
    @Bean PasswordEncoder passwordEncoder() {
        // JDK-supported algorithm, per-user 16-byte salt, no bcrypt byte truncation.
        var encoder = new Pbkdf2PasswordEncoder("", 16, 600_000, 256);
        encoder.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder("pbkdf2-sha256-v1", Map.of("pbkdf2-sha256-v1", encoder));
    }
}
