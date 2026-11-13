package com.swiftdrop.merchant.merchantapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MerchantApiKeyFilter extends OncePerRequestFilter {

    private final MerchantApiKeyAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public MerchantApiKeyFilter(
            MerchantApiKeyAuthenticator authenticator,
            ObjectMapper objectMapper
    ) {
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        MerchantKeyDetails merchant;

        try {
            merchant = authenticator.authenticate(
                    request.getHeader("X-API-Key")
            );
        } catch (ResponseStatusException exception) {
            response.setStatus(exception.getStatusCode().value());
            response.setContentType("application/json");

            objectMapper.writeValue(
                    response.getOutputStream(),
                    Map.of(
                            "error",
                            exception.getReason() == null
                                    ? "API key authentication failed"
                                    : exception.getReason()
                    )
            );

            return;
        }

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        merchant,
                        null,
                        List.of(new SimpleGrantedAuthority("MERCHANT_API"))
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}