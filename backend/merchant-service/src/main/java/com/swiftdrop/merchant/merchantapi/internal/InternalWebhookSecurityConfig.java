package com.swiftdrop.merchant.merchantapi.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class InternalWebhookSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain internalWebhookChain(
            HttpSecurity http,
            @Value("${swiftdrop.internal.webhook-token:}") String token
    ) throws Exception {

        var tokenFilter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain chain
            ) throws ServletException, IOException {

                if (token.length() < 32) {
                    response.setStatus(503);
                    response.setHeader("Cache-Control", "no-store");
                    return;
                }

                String received =
                        request.getHeader("X-SwiftDrop-Internal-Token");

                boolean valid = received != null
                        && MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        received.getBytes(StandardCharsets.UTF_8)
                );

                if (!valid) {
                    response.setStatus(401);
                    response.setHeader("Cache-Control", "no-store");
                    return;
                }

                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "notification-service",
                                null,
                                List.of(new SimpleGrantedAuthority(
                                        "INTERNAL_WEBHOOK"
                                ))
                        )
                );

                SecurityContextHolder.setContext(context);
                try {
                    chain.doFilter(request, response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        };

        return http
                .securityMatcher(new org.springframework.security.web.util.matcher.RegexRequestMatcher(
                        "^/internal/merchants/[0-9]+/webhook$", null
                ))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        tokenFilter,
                        AnonymousAuthenticationFilter.class
                )
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority("INTERNAL_WEBHOOK"))
                .build();
    }
}
