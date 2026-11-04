package com.tagme.auth;

public final class PasswordPolicy {
    public static final int MIN = 15;
    public static final int MAX = 64;
    private PasswordPolicy() { }
    public static void validate(String value) {
        int size = value == null ? 0 : value.codePointCount(0, value.length());
        if (size < MIN || size > MAX) throw new AuthException(400, "PASSWORD_POLICY", "비밀번호는 공백을 포함해 15~64자로 설정해 주세요.");
    }
}
