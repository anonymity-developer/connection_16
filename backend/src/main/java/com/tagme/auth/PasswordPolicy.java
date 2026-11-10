package com.tagme.auth;

public final class PasswordPolicy {
    public static final int MIN = 8;
    public static final int MAX = 64;
    private PasswordPolicy() { }
    public static void validate(String value) {
        validate(value, MIN);
    }
    static void validateAdmin(String value) {
        validate(value, 15);
    }
    private static void validate(String value, int minimum) {
        int size = value == null ? 0 : value.codePointCount(0, value.length());
        if (size < minimum || size > MAX || value.isBlank()) throw new AuthException(400, "PASSWORD_POLICY", "비밀번호는 공백을 포함해 " + minimum + "~" + MAX + "자로 설정해 주세요.");
    }
}
