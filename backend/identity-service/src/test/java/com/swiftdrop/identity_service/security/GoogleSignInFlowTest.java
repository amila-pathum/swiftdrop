package com.swiftdrop.identity_service.security;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ClientSettings;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole Google sign-in flow — start, Google callback, one-time code exchange,
 * authenticated API call — against real PostgreSQL and a fake Google that runs in
 * this JVM. The fake issues ID tokens signed with a test key and checks the PKCE
 * verifier; no real Google credentials or network access are involved.
 */
@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.frontend-origin=http://localhost:3000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class GoogleSignInFlowTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    private static final String CLIENT_ID = "test-client-id.apps.example.test";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "http://localhost:8080/login/oauth2/code/google";
    private static final String CALLBACK = "http://localhost:3000/auth/google/callback";
    private static final String VERIFIER = "frontend-pkce-verifier-0123456789-abcdefghijklmnop";

    private static final FakeGoogle google = FakeGoogle.start();

    @TestConfiguration
    static class FakeGoogleRegistration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration
                .withRegistrationId("google")
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope("openid", "profile", "email")
                .authorizationUri(google.url("/authorize"))
                .tokenUri(google.url("/token"))
                .jwkSetUri(google.url("/jwks"))
                .issuerUri(google.issuer())
                .userNameAttributeName("sub")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .build());
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterAll
    static void stopFakeGoogle() {
        google.server.stop(0);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE oauth_login_codes, user_identities, users RESTART IDENTITY CASCADE");
        google.reset();
    }

    @Test
    void newGoogleUserSignsInAndExchangesTheCodeForTheUsualLoginResponse() throws Exception {
        Started started = start();
        assertThat(started.param("code_challenge_method")).isEqualTo("S256");
        assertThat(started.param("client_id")).isEqualTo(CLIENT_ID);
        assertThat(started.param("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(started.param("scope")).isEqualTo("openid profile email");

        google.nextUser("google-sub-1", "New.Person@Gmail.com", true, "New Person");
        String redirect = callback(started);

        // Google received the PKCE verifier matching the challenge it was shown.
        assertThat(challengeOf(google.lastTokenRequest.get("code_verifier")))
            .isEqualTo(started.param("code_challenge"));
        assertThat(google.lastTokenRequest).containsEntry("redirect_uri", REDIRECT_URI);
        assertThat(google.lastTokenRequest).doesNotContainKey("client_secret");
        // The browser gets only a one-time code, and the OAuth session is gone.
        assertThat(redirect).startsWith(CALLBACK + "#code=").doesNotContain("eyJ");
        assertThat(started.session.isInvalid()).isTrue();

        MvcResult login = mockMvc.perform(exchange(codeFrom(redirect), VERIFIER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.email").value("new.person@gmail.com"))
            .andExpect(jsonPath("$.user.name").value("New Person"))
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new.person@gmail.com"));
    }

    @Test
    void theCodeCanBeExchangedOnlyOnce() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        String code = codeFrom(callback(start()));

        mockMvc.perform(exchange(code, VERIFIER)).andExpect(status().isOk());
        mockMvc.perform(exchange(code, VERIFIER))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired sign-in code"));
    }

    @Test
    void theCodeIsUselessWithoutTheFrontendVerifier() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        String code = codeFrom(callback(start()));

        mockMvc.perform(exchange(code, "an-attacker-who-only-saw-the-url-0123456789"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(exchange(code, VERIFIER)).andExpect(status().isUnauthorized());
    }

    @Test
    void aReturningGoogleUserGetsTheSameAccount() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        mockMvc.perform(exchange(codeFrom(callback(start())), VERIFIER)).andExpect(status().isOk());

        google.nextUser("google-sub-1", "person@gmail.com", true, "Changed Name");
        mockMvc.perform(exchange(codeFrom(callback(start())), VERIFIER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.id").value(1))
            .andExpect(jsonPath("$.user.name").value("Person"));
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void anExistingPasswordAccountIsNotLinkedAndKeepsWorking() throws Exception {
        users.save(new User("Existing", "existing@example.com",
            passwordEncoder.encode("password123"), Role.CUSTOMER, UserStatus.ACTIVE));

        google.nextUser("google-sub-2", "Existing@Example.com", true, "Someone Else");
        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=account_exists");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_identities", Integer.class)).isZero();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"existing@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.name").value("Existing"));
    }

    @Test
    void anUnverifiedGoogleEmailIsRefused() throws Exception {
        google.nextUser("google-sub-3", "unverified@gmail.com", false, "Unverified");

        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=email_unverified");
        assertThat(users.count()).isZero();
    }

    @Test
    void aSuspendedGoogleUserIsRefused() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        callback(start());
        jdbc.update("UPDATE users SET status = 'SUSPENDED'");

        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=account_inactive");
    }

    @Test
    void aSignInWithoutAValidClientChallengeNeverReachesGoogle() throws Exception {
        for (String challenge : new String[] {null, "", "too-short", "x".repeat(42) + "!"}) {
            var request = get("/oauth2/authorization/google");
            if (challenge != null) {
                request.param("client_challenge", challenge);
            }
            mockMvc.perform(request)
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                    .isEqualTo(CALLBACK + "#error=google_failed"));
        }
    }

    @Test
    void aCallbackWithoutAStartedSignInIsRefused() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google").param("code", "c").param("state", "s"))
            .andExpect(status().isFound())
            .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo(CALLBACK + "#error=google_failed"));
        assertThat(google.lastTokenRequest).isNull();
    }

    @Test
    void aTamperedStateIsRefused() throws Exception {
        Started started = start();
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");

        MvcResult result = mockMvc.perform(get("/login/oauth2/code/google")
                .param("code", "google-authorization-code")
                .param("state", started.param("state") + "x")
                .session(started.session))
            .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(CALLBACK + "#error=google_failed");
        assertThat(google.lastTokenRequest).isNull();
    }

    @Test
    void aUserWhoCancelsAtGoogleIsSentBackWithAnError() throws Exception {
        Started started = start();

        MvcResult result = mockMvc.perform(get("/login/oauth2/code/google")
                .param("error", "access_denied")
                .param("state", started.param("state"))
                .session(started.session))
            .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo(CALLBACK + "#error=google_failed");
    }

    @Test
    void anIdTokenNotSignedByGoogleIsRefused() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        google.signingKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();

        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=google_failed");
        assertThat(users.count()).isZero();
    }

    @Test
    void anIdTokenForAnotherClientIsRefused() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        google.audience = "some-other-client";

        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=google_failed");
        assertThat(users.count()).isZero();
    }

    @Test
    void anIdTokenWithAnotherNonceIsRefused() throws Exception {
        Started started = start();
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        google.nonce = "nonce-from-another-sign-in";

        assertThat(callback(started)).isEqualTo(CALLBACK + "#error=google_failed");
        assertThat(users.count()).isZero();
    }

    @Test
    void anIdTokenFromAnotherIssuerIsRefused() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        google.issuerClaim = "https://issuer.example.test";

        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=google_failed");
        assertThat(users.count()).isZero();
    }

    @Test
    void anExpiredIdTokenIsRefused() throws Exception {
        google.nextUser("google-sub-1", "person@gmail.com", true, "Person");
        google.lifetimeSeconds = -600;

        assertThat(callback(start())).isEqualTo(CALLBACK + "#error=google_failed");
        assertThat(users.count()).isZero();
    }

    @Test
    void otherOAuthPathsAreRefused() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/github").param("client_challenge", challengeOf(VERIFIER)))
            .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo(CALLBACK + "#error=google_failed"));
        mockMvc.perform(get("/oauth2/anything"))
            .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                .isEqualTo(CALLBACK + "#error=google_failed"));
    }

    @Test
    void theExchangeValidatesItsInput() throws Exception {
        mockMvc.perform(post("/api/auth/google/exchange").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"abc\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));
    }

    // --- helpers -------------------------------------------------------------

    private record Started(MockHttpSession session, Map<String, String> params) {
        String param(String name) {
            return params.get(name);
        }
    }

    private Started start() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google")
                .param("client_challenge", challengeOf(VERIFIER)))
            .andExpect(status().isFound())
            .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(google.url("/authorize"));

        Map<String, String> params = new HashMap<>();
        UriComponentsBuilder.fromUriString(location).build().getQueryParams()
            .forEach((name, values) -> params.put(name,
                URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
        google.nonce = params.get("nonce");
        return new Started((MockHttpSession) result.getRequest().getSession(false), params);
    }

    /** Simulates Google redirecting the browser back; returns where the browser goes next. */
    private String callback(Started started) throws Exception {
        return mockMvc.perform(get("/login/oauth2/code/google")
                .param("code", "google-authorization-code")
                .param("state", started.param("state"))
                .session(started.session))
            .andExpect(status().isFound())
            .andReturn().getResponse().getRedirectedUrl();
    }

    private static String codeFrom(String redirect) {
        assertThat(redirect).startsWith(CALLBACK + "#code=");
        return redirect.substring((CALLBACK + "#code=").length());
    }

    private static org.springframework.test.web.servlet.RequestBuilder exchange(String code, String verifier) {
        return post("/api/auth/google/exchange").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + code + "\",\"verifier\":\"" + verifier + "\"}");
    }

    private static String challengeOf(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /** Google's token and JWKS endpoints, enough for an OpenID Connect code flow. */
    static final class FakeGoogle {
        final HttpServer server;
        final RSAKey publishedKey;
        volatile RSAKey signingKey;
        volatile String audience;
        volatile String issuerClaim;
        volatile long lifetimeSeconds;
        volatile String nonce;
        volatile Map<String, Object> user;
        volatile Map<String, String> lastTokenRequest;

        private FakeGoogle(HttpServer server, RSAKey key) {
            this.server = server;
            this.publishedKey = key;
        }

        static FakeGoogle start() {
            try {
                RSAKey key = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                FakeGoogle google = new FakeGoogle(server, key);
                server.createContext("/token", google::token);
                server.createContext("/jwks", exchange -> respond(exchange, 200,
                    new JWKSet(key.toPublicJWK()).toString()));
                server.start();
                google.reset();
                return google;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        String issuer() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        String url(String path) {
            return issuer() + path;
        }

        void reset() {
            signingKey = publishedKey;
            audience = CLIENT_ID;
            issuerClaim = issuer();
            lifetimeSeconds = 300;
            nonce = null;
            user = null;
            lastTokenRequest = null;
        }

        void nextUser(String subject, String email, boolean emailVerified, String name) {
            user = Map.of("sub", subject, "email", email, "email_verified", emailVerified, "name", name);
        }

        private void token(HttpExchange exchange) throws IOException {
            Map<String, String> form = new HashMap<>();
            for (String pair : new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8).split("&")) {
                String[] parts = pair.split("=", 2);
                form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
            lastTokenRequest = form;

            String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
            if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    || form.get("code_verifier") == null) {
                respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            try {
                Instant now = Instant.now();
                JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuerClaim)
                    .audience(audience)
                    .issueTime(Date.from(now.plusSeconds(Math.min(0, lifetimeSeconds))))
                    .expirationTime(Date.from(now.plusSeconds(lifetimeSeconds)))
                    .claim("nonce", nonce);
                user.forEach(claims::claim);
                SignedJWT idToken = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims.build());
                idToken.sign(new RSASSASigner(signingKey));
                respond(exchange, 200, """
                    {"access_token":"google-access-token","token_type":"Bearer","expires_in":300,
                     "scope":"openid profile email","id_token":"%s"}""".formatted(idToken.serialize()));
            } catch (Exception exception) {
                respond(exchange, 500, "{}");
            }
        }

        private static void respond(HttpExchange exchange, int status, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
