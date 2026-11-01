package com.swiftdrop.identity_service.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Ends a Google sign-in by sending the browser back to the frontend callback page.
 * Results travel in the URL fragment, which browsers never send to a server.
 * The short-lived OAuth session is discarded either way.
 */
@Component
public class GoogleSignInRedirects {

    static final String CALLBACK_PATH = "/auth/google/callback";

    private final String callbackUrl;

    public GoogleSignInRedirects(@Value("${swiftdrop.frontend-origin}") String frontendOrigin) {
        this.callbackUrl = frontendOrigin.replaceAll("/+$", "") + CALLBACK_PATH;
    }

    /** {@code code} is base64url, so it needs no escaping. */
    public void success(HttpServletRequest request, HttpServletResponse response, String code)
            throws IOException {
        finish(request, response, "#code=" + code);
    }

    /** {@code errorCode} is one of the fixed reason codes, never user data. */
    public void failure(HttpServletRequest request, HttpServletResponse response, String errorCode)
            throws IOException {
        finish(request, response, "#error=" + errorCode);
    }

    private void finish(HttpServletRequest request, HttpServletResponse response, String fragment)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(callbackUrl + fragment);
    }
}
