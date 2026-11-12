package com.tagme.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @Test void policyCountsUnicodeCodePointsAndDoesNotTrim() {
        assertThatCode(() -> PasswordPolicy.validate(" a valid password ")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(8))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("가".repeat(20))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("😀".repeat(20))).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("😀".repeat(8))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate(" ".repeat(8))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(7))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("가".repeat(21))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("😀".repeat(21))).isInstanceOf(AuthException.class);
    }
    @Test void administratorBootstrapKeepsItsSeparateExistingBounds() {
        assertThatCode(()->PasswordPolicy.validateAdmin("a".repeat(64))).doesNotThrowAnyException();
        assertThatThrownBy(()->PasswordPolicy.validateAdmin("a".repeat(14))).isInstanceOf(AuthException.class);
        assertThatThrownBy(()->PasswordPolicy.validateAdmin("a".repeat(65))).isInstanceOf(AuthException.class);
    }
    @Test void hashesAreSaltedAndSupportKoreanBeyondBcryptByteLimit() {
        var passwords = new AuthBeans().passwordEncoder();
        String value = "가".repeat(30);
        String first = passwords.encode(value);
        assertThat(first).doesNotContain(value).startsWith("{pbkdf2-sha256-v1}");
        assertThat(passwords.encode(value)).isNotEqualTo(first);
        assertThat(passwords.matches(value, first)).isTrue();
        assertThat(passwords.matches(value + " ", first)).isFalse();
    }
    @Test void sensitiveRequestToStringDoesNotRevealInputs() {
        assertThat(new AuthController.SignupRequest("sensitive-challenge", "012345", "secret password").toString()).doesNotContain("012345", "secret", "sensitive");
        assertThat(new AuthController.PasswordRequest("sensitive-challenge", "012345", "secret password").toString()).doesNotContain("012345", "secret", "sensitive");
        assertThat(new AuthController.LoginRequest("someone", "secret password").toString()).doesNotContain("someone", "secret");
    }
}
