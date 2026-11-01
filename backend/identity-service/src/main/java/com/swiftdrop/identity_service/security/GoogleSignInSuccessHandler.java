package com.swiftdrop.identity_service.security;

import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.exception.GoogleSignInException;
import com.swiftdrop.identity_service.service.GoogleAccountService;
import com.swiftdrop.identity_service.service.GoogleAccountService.GoogleProfile;
import com.swiftdrop.identity_service.service.OAuthLoginCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after Google has authenticated the user: finds or creates the SwiftDrop
 * account and hands the frontend a one-time code instead of a JWT.
 */
@Component
public class GoogleSignInSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleSignInSuccessHandler.class);

    private final GoogleAccountService googleAccounts;
    private final OAuthLoginCodeService loginCodes;
    private final GoogleSignInRedirects redirects;

    public GoogleSignInSuccessHandler(GoogleAccountService googleAccounts,
            OAuthLoginCodeService loginCodes, GoogleSignInRedirects redirects) {
        this.googleAccounts = googleAccounts;
        this.loginCodes = loginCodes;
        this.redirects = redirects;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        String challenge = ClientChallengeFilter.take(request);
        if (challenge == null || !(authentication.getPrincipal() instanceof OidcUser google)) {
            redirects.failure(request, response, "google_failed");
            return;
        }
        try {
            User user = googleAccounts.resolveUser(new GoogleProfile(google.getSubject(),
                google.getEmail(), Boolean.TRUE.equals(google.getEmailVerified()),
                google.getFullName()));
            redirects.success(request, response, loginCodes.issue(user, challenge));
        } catch (GoogleSignInException exception) {
            log.info(exception.getMessage());
            redirects.failure(request, response, exception.getReason().code());
        } catch (DataIntegrityViolationException exception) {
            // The same new Google account signed in twice at once; retrying works.
            log.info("Google sign-in raced with another sign-in for the same account");
            redirects.failure(request, response, "google_failed");
        }
    }
}
