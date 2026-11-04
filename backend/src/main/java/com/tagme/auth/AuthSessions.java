package com.tagme.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class AuthSessions {
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    public AuthSessions(SecurityContextRepository contexts, CsrfTokenRepository csrf, FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.contexts = contexts; this.csrf = csrf; this.sessions = sessions;
    }
    public void login(AuthRepository.Account account, HttpServletRequest request, HttpServletResponse response) {
        var principal = account.principal();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        request.getSession(true);
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession().setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getName());
        contexts.saveContext(context, request, response);
    }
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        csrf.saveToken(null, request, response);
    }
    public void revokeOld(AuthRepository.Account account) {
        try {
            sessions.findByPrincipalName(Long.toString(account.id())).values().forEach(session -> {
                SecurityContext context = session.getAttribute("SPRING_SECURITY_CONTEXT");
                var authentication = context == null ? null : context.getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)
                        || principal.version() != account.version()) sessions.deleteById(session.getId());
            });
        } catch (org.springframework.dao.DataAccessException ignored) {
            // The committed DB version is authoritative. Every subsequent
            // authenticated request checks it, even if Redis cleanup fails.
        }
    }
}
