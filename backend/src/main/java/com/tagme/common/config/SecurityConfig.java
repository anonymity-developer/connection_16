package com.tagme.common.config;

import jakarta.servlet.http.HttpServletResponse;
import com.tagme.auth.AuthRepository;
import com.tagme.auth.CredentialVersionFilter;
import com.tagme.auth.ConnectionSessions;
import com.tagme.auth.ConnectionActivityFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean
    CsrfTokenRepository csrfTokenRepository() { return CookieCsrfTokenRepository.withHttpOnlyFalse(); }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthRepository accounts, SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,ConnectionSessions connections) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/meta", "/api/v1/csrf", "/api/v1/auth/config", "/api/v1/event", "/api/v1/tags/pending", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/request", "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/password/reset", "/api/v1/tags/pending", "/api/v1/tags/inspect").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
                .addFilterAfter(new CredentialVersionFilter(accounts,connections), SecurityContextHolderFilter.class)
                .addFilterAfter(new ConnectionActivityFilter(connections), CredentialVersionFilter.class)
                .headers(headers -> headers.cacheControl(cache -> { }))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"요청 권한 또는 CSRF 토큰을 확인하세요.\"}");
                        }))
                .build();
    }
}
