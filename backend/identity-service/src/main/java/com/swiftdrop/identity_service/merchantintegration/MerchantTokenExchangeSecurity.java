package com.swiftdrop.identity_service.merchantintegration;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Not routed by API Gateway. Its service credential must never be sent to a browser. */
@Configuration(proxyBeanMethods = false)
public class MerchantTokenExchangeSecurity {
    @Bean @Order(0)
    SecurityFilterChain merchantTokenExchangeChain(HttpSecurity http,
            @Value("${merchant.order.service-token:${MERCHANT_ORDER_SERVICE_TOKEN:}}") String token) throws Exception {
        var guard = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                response.setHeader("Cache-Control", "no-store");
                if (token.length() < 32) { response.setStatus(503); return; }
                String supplied = request.getHeader("X-SwiftDrop-Merchant-Token");
                if (supplied == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) { response.setStatus(401); return; }
                chain.doFilter(request, response);
            }
        };
        return http.securityMatcher("/internal/merchant-order-tokens")
                .csrf(csrf -> csrf.disable()).httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(guard, UsernamePasswordAuthenticationFilter.class).build();
    }
}
