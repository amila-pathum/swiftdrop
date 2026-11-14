package com.swiftdrop.identity_service.security;

import tools.jackson.databind.ObjectMapper;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Authenticates requests carrying a SwiftDrop access token.
 *
 * <p>The token is only a pointer: the user is loaded again on every request and
 * the token is refused when the account can no longer sign in, or when its
 * email, role, status or token version differ from the token's (so a logout,
 * password reset, activation or suspension takes effect here immediately).
 *
 * <p>Only ACTIVE accounts receive their {@code ROLE_<role>} authority. An
 * account awaiting approval is authenticated but holds no authority, so it can
 * reach only endpoints open to every signed-in user (its own profile).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService,
            UserRepository userRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == 7) {
            reject(response);
            return;
        }

        try {
            TokenClaims claims = jwtService.parseClaims(authorization.substring(7));
            AuthenticatedUser claimsPrincipal = claims.principal();
            User user = userRepository.findById(claimsPrincipal.id())
                .filter(candidate -> candidate.getStatus().canSignIn())
                .orElseThrow(() -> new JwtException("Account unavailable"));
            if (!user.getEmail().equals(claimsPrincipal.email())
                    || user.getRole() != claimsPrincipal.role()
                    || user.getTokenVersion() != claims.tokenVersion()
                    || (claims.status() != null && claims.status() != user.getStatus())) {
                throw new JwtException("Stale token claims");
            }

            AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getRole());
            List<SimpleGrantedAuthority> authorities = switch (user.getStatus()) {
                case ACTIVE -> List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                case PENDING_APPROVAL -> List.of(
                    new SimpleGrantedAuthority("APPLICANT_" + user.getRole().name()));
                default -> List.of();
            };
            var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            reject(response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(),
            Map.of("error", "Invalid or expired access token"));
    }
}
