package com.swiftdrop.identity_service.config;

import com.swiftdrop.identity_service.repository.OAuthLoginCodeRepository;
import com.swiftdrop.identity_service.repository.UserIdentityRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production Google registration, with placeholder credentials. The start of
 * the flow is checked up to the redirect to Google; nothing is sent to Google.
 */
@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "swiftdrop.google.client-id=placeholder-client-id.apps.example.test",
    "swiftdrop.google.client-secret=placeholder-client-secret",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class GoogleSignInConfigTest {

    // base64url SHA-256 of a verifier; only its shape matters here.
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Autowired MockMvc mockMvc;
    @Autowired ClientRegistrationRepository registrations;
    @MockitoBean UserRepository userRepository;
    @MockitoBean UserIdentityRepository userIdentityRepository;
    @MockitoBean OAuthLoginCodeRepository oauthLoginCodeRepository;

    @Test
    void registersGoogleAsAnOpenIdConnectClientThatRequiresPkce() {
        ClientRegistration google = registrations.findByRegistrationId("google");

        assertThat(google.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(google.getRedirectUri()).isEqualTo("http://localhost:8080/login/oauth2/code/google");
        assertThat(google.getProviderDetails().getAuthorizationUri())
            .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(google.getProviderDetails().getIssuerUri()).isEqualTo("https://accounts.google.com");
    }

    @Test
    void startsTheFlowWithStateNonceAndAnS256Challenge() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/google")
                .param("client_challenge", CHALLENGE))
            .andExpect(status().isFound())
            .andReturn().getResponse().getRedirectedUrl();

        var params = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(params.getFirst("response_type")).isEqualTo("code");
        assertThat(params.getFirst("client_id")).isEqualTo("placeholder-client-id.apps.example.test");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        // Google gets its own PKCE challenge, not the frontend's.
        assertThat(params.getFirst("code_challenge")).isNotBlank().isNotEqualTo(CHALLENGE);
        assertThat(params.getFirst("state")).isNotBlank();
        assertThat(params.getFirst("nonce")).isNotBlank();
        assertThat(location).doesNotContain("placeholder-client-secret");
    }
}
