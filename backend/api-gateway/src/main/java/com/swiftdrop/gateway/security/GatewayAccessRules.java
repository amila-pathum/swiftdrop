package com.swiftdrop.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Optional;

/**
 * Which routed requests may pass without an access token, and which need a
 * particular role. Keep these in step with each service's own SecurityConfig:
 * the services check the token again, so this list may only be narrower.
 */
public final class GatewayAccessRules {

    private record Rule(HttpMethod method, PathPattern pattern) {
        boolean matches(HttpMethod requestMethod, PathContainer path) {
            return (method == null || method.equals(requestMethod)) && pattern.matches(path);
        }
    }

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    /** Open without a token (method {@code null} means any method). */
    private static final List<Rule> PUBLIC = List.of(
        // Identity Service: sign-up, sign-in, token refresh and password reset.
        post("/api/auth/register"),
        post("/api/auth/register/merchant"),
        post("/api/auth/register/courier"),
        post("/api/auth/login"),
        post("/api/auth/google/exchange"),
        post("/api/auth/refresh"),
        post("/api/auth/password-reset/request"),
        post("/api/auth/password-reset/confirm"),
        // Google sign-in browser redirects.
        any("/oauth2/authorization/google"),
        any("/login/oauth2/code/google"),
        // Public parcel tracking and its live status stream.
        get("/api/parcels/track/{trackingNumber}"),
        get("/api/notifications/parcels/{trackingNumber}/stream"),
        // Merchant order API: authenticated by X-API-Key in the Merchant Service.
        any("/api/merchants/integration"),
        any("/api/merchants/integration/**"));

    /**
     * EventSource cannot send headers, so the user's notification stream sends
     * the token as {@code ?access_token=}. Accepted on this path only.
     */
    private static final List<Rule> QUERY_TOKEN = List.of(get("/api/notifications/stream"));

    /** Coarse role checks; services still enforce their own, finer rules. */
    private static final List<Rule> ADMIN_ONLY = List.of(any("/api/admin/**"));

    private GatewayAccessRules() { }

    public static boolean isPublic(HttpMethod method, String path) {
        return matchesAny(PUBLIC, method, path);
    }

    public static boolean allowsQueryToken(HttpMethod method, String path) {
        return matchesAny(QUERY_TOKEN, method, path);
    }

    /** The role a path requires at the gateway, if any. */
    public static Optional<String> requiredRole(HttpMethod method, String path) {
        return matchesAny(ADMIN_ONLY, method, path) ? Optional.of("ADMIN") : Optional.empty();
    }

    private static boolean matchesAny(List<Rule> rules, HttpMethod method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return rules.stream().anyMatch(rule -> rule.matches(method, container));
    }

    private static Rule get(String pattern) {
        return new Rule(HttpMethod.GET, PARSER.parse(pattern));
    }

    private static Rule post(String pattern) {
        return new Rule(HttpMethod.POST, PARSER.parse(pattern));
    }

    private static Rule any(String pattern) {
        return new Rule(null, PARSER.parse(pattern));
    }
}
