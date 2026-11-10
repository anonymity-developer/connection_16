package com.tagme.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    public record ConfigResponse(boolean available, int passwordMinLength, int passwordMaxLength,boolean participantAvailable,boolean adminAvailable) { }
    public record UserResponse(long id, String username, String nickname, boolean onboardingRequired,boolean admin) {
        static UserResponse from(AuthRepository.Account account) { return new UserResponse(account.id(), account.username(), account.nickname(), account.onboardingRequired(),"ADMIN".equals(account.role())); }
    }
    public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record OtpRequest(@NotNull OtpStore.Purpose purpose, @Size(max = 64) String username) { }
    public record SignupRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String challengeId,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String code, @NotBlank @Size(max = 128) String password) {
        @Override public String toString() { return "SignupRequest[redacted]"; }
    }
    public record PasswordRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String challengeId,
                                  @NotBlank @Pattern(regexp = "[0-9]{6}") String code, @NotBlank @Size(max = 128) String newPassword) {
        @Override public String toString() { return "PasswordRequest[redacted]"; }
    }
    private final AuthService service;
    private final AuthProperties properties;
    private final AuthSessions sessions;
    private final AuthRepository accounts;
    private final LocalAdminService admins;
    public AuthController(AuthService service, AuthProperties properties, AuthSessions sessions, AuthRepository accounts,LocalAdminService admins) {
        this.service = service; this.properties = properties; this.sessions = sessions; this.accounts = accounts; this.admins=admins;
    }
    @GetMapping("/auth/config")
    public ConfigResponse config() { boolean admin=accounts.localAdminAvailable();return new ConfigResponse(properties.enabled()||admin, PasswordPolicy.MIN, PasswordPolicy.MAX,properties.enabled(),admin); }
    @PostMapping("/auth/otp/request")
    public AuthService.OtpResponse otp(@Valid @RequestBody OtpRequest body, Authentication authentication, HttpServletRequest request) {
        return service.request(body.purpose(), body.username(), principalOrNull(authentication), sessionId(request), request.getRemoteAddr());
    }
    @PostMapping("/auth/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = LocalAdminService.adminLogin(body.username())?admins.login(body.username(),body.password(),request.getRemoteAddr()):service.login(body.username(), body.password(), request.getRemoteAddr());
        sessions.login(account, request, response); return UserResponse.from(account);
    }
    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = service.signup(body.challengeId(), body.code(), body.password(), request.getRemoteAddr());
        sessions.login(account, request, response); return UserResponse.from(account);
    }
    @PutMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@Valid @RequestBody PasswordRequest body, Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var principal = principal(authentication);
        var account = service.change(body.challengeId(), body.code(), body.newPassword(), OtpStore.Purpose.PASSWORD_CHANGE, principal, sessionId(request), request.getRemoteAddr());
        sessions.revokeOld(account); sessions.logout(request, response);
    }
    @PostMapping("/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody PasswordRequest body, HttpServletRequest request, HttpServletResponse response) {
        var account = service.change(body.challengeId(), body.code(), body.newPassword(), OtpStore.Purpose.PASSWORD_RESET, null, null, request.getRemoteAddr());
        sessions.revokeOld(account); sessions.logout(request, response);
    }
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) { sessions.logout(request, response); }
    @GetMapping("/users/me")
    public UserResponse me(Authentication authentication) {
        return accounts.byId(principal(authentication).userId()).map(UserResponse::from).orElseThrow(AuthException::invalid);
    }
    private AuthPrincipal principal(Authentication authentication) {
        var principal = principalOrNull(authentication);
        if (principal == null) throw new AuthException(401, "UNAUTHENTICATED", "로그인이 필요합니다.");
        return principal;
    }
    private AuthPrincipal principalOrNull(Authentication authentication) { return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal ? principal : null; }
    private String sessionId(HttpServletRequest request) { var session = request.getSession(false); return session == null ? null : session.getId(); }
}
