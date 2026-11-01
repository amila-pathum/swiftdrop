package com.swiftdrop.identity_service.config;

import com.swiftdrop.identity_service.security.ClientChallengeFilter;
import com.swiftdrop.identity_service.security.GoogleSignInRedirects;
import com.swiftdrop.identity_service.security.GoogleSignInSuccessHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ClientSettings;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.util.StringUtils;

/**
 * Google sign-in (OpenID Connect, authorization code with PKCE). It has its own
 * filter chain, ordered before the stateless API chain, because the browser
 * redirect flow needs a short-lived session to hold state, nonce and PKCE
 * verifier between {@code /oauth2/authorization/google} and the callback.
 */
@Configuration
public class GoogleSignInConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleSignInConfig.class);

    /**
     * The Google client registration. With neither credential set, Google sign-in
     * is off; with only one of them, startup fails rather than silently running
     * without Google or with an unusable client.
     */
    @Configuration
    @Conditional(GoogleCredentialConfigured.class)
    static class GoogleClientRegistrationConfig {

        @Bean
        ClientRegistrationRepository googleClientRegistrationRepository(
                @Value("${swiftdrop.google.client-id:}") String clientId,
                @Value("${swiftdrop.google.client-secret:}") String clientSecret,
                @Value("${swiftdrop.google.redirect-uri:http://localhost:8080/login/oauth2/code/google}")
                String redirectUri) {
            if (!StringUtils.hasText(clientSecret)) {
                throw new IllegalStateException(
                    "GOOGLE_CLIENT_SECRET must be set when GOOGLE_CLIENT_ID is set");
            }
            if (!StringUtils.hasText(clientId)) {
                throw new IllegalStateException(
                    "GOOGLE_CLIENT_ID must be set when GOOGLE_CLIENT_SECRET is set");
            }
            return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                // PKCE is already Spring Security 7's default; stated so it cannot be lost.
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .build());
        }
    }

    @Bean
    @Order(1)
    SecurityFilterChain googleSignInFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            GoogleSignInSuccessHandler successHandler,
            GoogleSignInRedirects redirects) throws Exception {
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        String unmatchedError = registrations == null ? "google_unavailable" : "google_failed";

        http
            .securityMatcher("/oauth2/**", "/login/oauth2/**")
            // Only GET redirects pass through here; state and PKCE protect them.
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())
            .requestCache(cache -> cache.disable())
            // The Google login is never kept as a session login; the frontend
            // gets a JWT through the one-time code exchange instead.
            .securityContext(context -> context
                .securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    redirects.failure(request, response, unmatchedError))
                .accessDeniedHandler((request, response, exception) ->
                    redirects.failure(request, response, unmatchedError)));

        if (registrations != null) {
            http
                .addFilterBefore(new ClientChallengeFilter(redirects),
                    OAuth2AuthorizationRequestRedirectFilter.class)
                .oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(registrations)
                    .successHandler(successHandler)
                    .failureHandler((request, response, exception) -> {
                        log.info("Google sign-in failed: {}", errorCode(exception));
                        redirects.failure(request, response, "google_failed");
                    }));
        }
        return http.build();
    }

    private static String errorCode(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth
            ? oauth.getError().getErrorCode()
            : exception.getClass().getSimpleName();
    }

    /** Either Google credential is set, so the registration must be built (or refused). */
    static class GoogleCredentialConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            return StringUtils.hasText(environment.getProperty("swiftdrop.google.client-id"))
                || StringUtils.hasText(environment.getProperty("swiftdrop.google.client-secret"));
        }
    }
}
