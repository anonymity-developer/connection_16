package com.tagme.auth;

public final class PasswordPolicy {
    public static final int MIN = 8;
    public static final int MAX = 20;
    private PasswordPolicy() { }
    public static void validate(String value) {
        validate(value, MIN, MAX);
    }
    static void validateAdmin(String value) {
        validate(value, 15, 64);
    }
    private static void validate(String value, int minimum, int maximum) {
        int size = value == null ? 0 : value.codePointCount(0, value.length());
        if (size < minimum || size > maximum || value.isBlank()) throw new AuthException(400, "PASSWORD_POLICY", "비밀번호는 공백을 포함해 " + minimum + "~" + maximum + "자로 설정해 주세요.");
    }
}
