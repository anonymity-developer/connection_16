package com.tagme.auth;

public class AuthException extends RuntimeException {
    private final int status;
    private final String code;
    private final int retryAfter;
    public AuthException(int status, String code, String message) { this(status, code, message, 0); }
    public AuthException(int status, String code, String message, int retryAfter) {
        super(message); this.status = status; this.code = code; this.retryAfter = retryAfter;
    }
    public int status() { return status; }
    public String code() { return code; }
    public int retryAfter() { return retryAfter; }
    public static AuthException unavailable() { return new AuthException(503, "AUTH_UNAVAILABLE", "인증 서비스를 사용할 수 없어요. 잠시 후 다시 시도해 주세요."); }
    public static AuthException invalid() { return new AuthException(401, "INVALID_CHALLENGE", "인증번호가 유효하지 않아요. 번호를 확인하거나 새로 받아주세요."); }
    public static AuthException loginFailed() { return new AuthException(401, "LOGIN_FAILED", "아이디 또는 TAG ME 비밀번호를 확인해 주세요."); }
    public static AuthException conflict() { return new AuthException(409, "ACCOUNT_CONFLICT", "계정 상태를 확인할 수 없어요. 로그인 또는 비밀번호 재설정을 이용해 주세요."); }
    public static AuthException limited(int seconds) { return new AuthException(429, "RATE_LIMITED", "요청이 많아요. 잠시 기다린 뒤 다시 시도해 주세요.", Math.max(1, seconds)); }
}
