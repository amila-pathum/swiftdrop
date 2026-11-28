package com.swiftdrop.notification.internal;

import com.swiftdrop.notification.client.IdentityServiceClient;
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

/** /internal/emails/** is for other SwiftDrop services only (not routed by API Gateway). */
@Configuration(proxyBeanMethods = false)
public class InternalEmailSecurity {

    @Bean
    @Order(1)
    SecurityFilterChain internalEmailChain(HttpSecurity http,
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
                String supplied = request.getHeader(IdentityServiceClient.TOKEN_HEADER);
                if (supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(401);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        return http.securityMatcher("/internal/emails/**")
                .csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(guard, UsernamePasswordAuthenticationFilter.class).build();
    }
}
