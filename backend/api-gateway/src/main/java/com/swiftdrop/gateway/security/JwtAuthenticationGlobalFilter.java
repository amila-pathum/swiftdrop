package com.swiftdrop.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Checks the caller's access token on every routed request, then tells the
 * service who the caller is.
 *
 * <ul>
 *   <li>Headers {@code X-User-Id}, {@code X-User-Role} and {@code X-User-Status}
 *       sent by the client are always removed, then set from a verified token.</li>
 *   <li>Public paths ({@link GatewayAccessRules#isPublic}) pass without a token.
 *       If a valid token is present the identity headers are still added.</li>
 *   <li>Every other path needs a valid token: otherwise 401.</li>
 *   <li>Paths with a gateway role rule need that role on an active account:
 *       otherwise 403.</li>
 * </ul>
 *
 * <p>The {@code Authorization} header is forwarded unchanged: every service
 * verifies the token again (defence in depth) and does not trust these headers
 * on their own.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String USER_STATUS = "X-User-Status";

    private static final String BEARER = "Bearer ";

    private final GatewayTokenVerifier verifier;

    public JwtAuthenticationGlobalFilter(GatewayTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().pathWithinApplication().value();

        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(withIdentity(exchange, Optional.empty()));
        }

        Optional<String> token = tokenFrom(request, method, path);
        Optional<GatewayPrincipal> principal = token.flatMap(verifier::verify);

        if (GatewayAccessRules.isPublic(method, path)) {
            return chain.filter(withIdentity(exchange, principal));
        }
        if (principal.isEmpty()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, token.isPresent()
                ? "Invalid or expired access token" : "Authentication is required");
        }
        Optional<String> requiredRole = GatewayAccessRules.requiredRole(method, path);
        if (requiredRole.isPresent() && (!principal.get().isActive()
                || !requiredRole.get().equals(principal.get().role()))) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Access is denied");
        }
        return chain.filter(withIdentity(exchange, principal));
    }

    /** Before routing and every other gateway filter. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private static Optional<String> tokenFrom(ServerHttpRequest request, HttpMethod method,
            String path) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            String value = header.substring(BEARER.length()).trim();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }
        if (header == null && GatewayAccessRules.allowsQueryToken(method, path)) {
            return Optional.ofNullable(request.getQueryParams().getFirst("access_token"))
                .filter(value -> !value.isBlank());
        }
        return Optional.empty();
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange,
            Optional<GatewayPrincipal> principal) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(USER_ID);
            headers.remove(USER_ROLE);
            headers.remove(USER_STATUS);
            principal.ifPresent(user -> {
                headers.set(USER_ID, user.userId());
                headers.set(USER_ROLE, user.role());
                if (user.status() != null) {
                    headers.set(USER_STATUS, user.status());
                }
            });
        }).build();
        return exchange.mutate().request(request).build();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status,
            String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
