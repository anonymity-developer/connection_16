package com.tagme.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ConnectionActivityFilter extends OncePerRequestFilter {
    private final ConnectionSessions connections;
    public ConnectionActivityFilter(ConnectionSessions connections){this.connections=connections;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
        var auth=SecurityContextHolder.getContext().getAuthentication();var session=request.getSession(false);
        if(auth!=null&&auth.getPrincipal() instanceof AuthPrincipal&&session!=null)connections.touch(session);
        chain.doFilter(request,response);
    }
}
