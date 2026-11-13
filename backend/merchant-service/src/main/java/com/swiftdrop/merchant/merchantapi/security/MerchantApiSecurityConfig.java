package com.swiftdrop.merchant.merchantapi.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration
public class MerchantApiSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain merchantApiSecurityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            ObjectProvider<MerchantApiKeyLookup> lookupProvider
    ) throws Exception {

        MerchantApiKeyLookup lookup = apiKey -> {
            MerchantApiKeyLookup implementation =
                    lookupProvider.getIfAvailable();

            if (implementation == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Merchant API authentication is unavailable"
                );
            }

            return implementation.findByApiKey(apiKey);
        };

        MerchantApiKeyAuthenticator authenticator =
                new MerchantApiKeyAuthenticator(lookup);

        MerchantApiKeyFilter filter =
                new MerchantApiKeyFilter(authenticator, objectMapper);

        return http
                .securityMatcher(
                        "/api/merchants/integration",
                        "/api/merchants/integration/**"
                )
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().hasAuthority("MERCHANT_API")
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            objectMapper.writeValue(
                                    response.getOutputStream(),
                                    Map.of("error", "API key authentication is required")
                            );
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            objectMapper.writeValue(
                                    response.getOutputStream(),
                                    Map.of("error", "Access is denied")
                            );
                        })
                )
                .addFilterBefore(
                        filter,
                        AnonymousAuthenticationFilter.class
                )
                .build();
    }
}