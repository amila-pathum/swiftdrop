package com.swiftdrop.identity_service.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Service-to-service lookups under /internal/users. API Gateway does not route
 * /internal/**; callers send the shared service token, which never reaches a browser.
 */
@Configuration(proxyBeanMethods = false)
public class InternalServiceSecurity {

    public static final String HEADER = "X-SwiftDrop-Internal-Token";

    @Bean
    @Order(1)
    SecurityFilterChain internalUsersChain(HttpSecurity http,
            @Value("${swiftdrop.internal.service-token:}") String token) throws Exception {
        var guard = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                response.setHeader("Cache-Control", "no-store");
                if (token.length() < 32) {
                    response.setStatus(503);
                    return;
                }
                String supplied = request.getHeader(HEADER);
                if (supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(401);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        return http.securityMatcher("/internal/users/**", "/internal/users")
                .csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(guard, UsernamePasswordAuthenticationFilter.class).build();
    }
}
