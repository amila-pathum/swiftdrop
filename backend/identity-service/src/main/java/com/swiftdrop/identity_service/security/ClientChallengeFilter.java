package com.swiftdrop.identity_service.security;

import com.swiftdrop.identity_service.service.OAuthLoginCodeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Captures the frontend's {@code client_challenge} when a Google sign-in starts
 * ({@code /oauth2/authorization/google?client_challenge=...}) and keeps it in the
 * OAuth session until the callback, where the one-time code is bound to it.
 * A sign-in without a valid challenge is refused before reaching Google.
 */
public class ClientChallengeFilter extends OncePerRequestFilter {

    static final String START_PATH = "/oauth2/authorization/google";
    static final String PARAMETER = "client_challenge";
    private static final String SESSION_ATTRIBUTE = ClientChallengeFilter.class.getName() + ".CHALLENGE";

    private final GoogleSignInRedirects redirects;

    public ClientChallengeFilter(GoogleSignInRedirects redirects) {
        this.redirects = redirects;
    }

    /** Removes and returns the challenge stored for this sign-in, or null. */
    public static String take(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object challenge = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return challenge instanceof String value ? value : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/oauth2/authorization/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String challenge = request.getParameter(PARAMETER);
        if (!START_PATH.equals(request.getRequestURI())
                || !OAuthLoginCodeService.isValidChallenge(challenge)) {
            redirects.failure(request, response, "google_failed");
            return;
        }
        request.getSession().setAttribute(SESSION_ATTRIBUTE, challenge);
        chain.doFilter(request, response);
    }
}
