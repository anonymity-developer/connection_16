package com.tagme.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CredentialVersionFilter extends OncePerRequestFilter {
    private final AuthRepository accounts;
    private final ConnectionSessions connections;
    public CredentialVersionFilter(AuthRepository accounts,ConnectionSessions connections) { this.accounts = accounts;this.connections=connections; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            try {
                boolean valid = accounts.byId(principal.userId()).filter(a -> "ACTIVE".equals(a.status())
                        && Objects.equals(a.version(), principal.version()) && Objects.equals(a.role(), principal.role())).isPresent();
                if (!valid) {
                    var session = request.getSession(false); connections.close(session,"REVOKED"); if (session != null) session.invalidate();
                    SecurityContextHolder.clearContext();
                    error(response, 401, "SESSION_EXPIRED", "로그인이 만료됐어요. 다시 로그인해 주세요."); return;
                }
            } catch (DataAccessException failure) { error(response, 503, "AUTH_UNAVAILABLE", "인증 서비스를 사용할 수 없어요."); return; }
        }
        chain.doFilter(request, response);
    }
    private void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
