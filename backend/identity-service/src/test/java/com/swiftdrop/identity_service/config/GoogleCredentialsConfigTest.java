package com.swiftdrop.identity_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup with each combination of Google credentials. Placeholder values only;
 * nothing is sent to Google.
 */
class GoogleCredentialsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(GoogleSignInConfig.GoogleClientRegistrationConfig.class);

    @Test
    void neitherCredentialDisablesGoogleSignInAndStartsNormally() {
        runner.withPropertyValues("swiftdrop.google.client-id=", "swiftdrop.google.client-secret=")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            });
    }

    @Test
    void onlyTheClientIdFailsStartup() {
        runner.withPropertyValues("swiftdrop.google.client-id=placeholder-id.apps.example.test")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("GOOGLE_CLIENT_SECRET must be set when GOOGLE_CLIENT_ID is set");
            });
    }

    @Test
    void onlyTheClientSecretFailsStartup() {
        runner.withPropertyValues("swiftdrop.google.client-secret=placeholder-secret")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("GOOGLE_CLIENT_ID must be set when GOOGLE_CLIENT_SECRET is set");
            });
    }

    @Test
    void aBlankValueCountsAsMissing() {
        runner.withPropertyValues("swiftdrop.google.client-id=   ",
                "swiftdrop.google.client-secret=placeholder-secret")
            .run(context -> assertThat(context.getStartupFailure()).rootCause()
                .hasMessage("GOOGLE_CLIENT_ID must be set when GOOGLE_CLIENT_SECRET is set"));
    }

    @Test
    void bothCredentialsEnableGoogleSignIn() {
        runner.withPropertyValues("swiftdrop.google.client-id=placeholder-id.apps.example.test",
                "swiftdrop.google.client-secret=placeholder-secret")
            .run(context -> {
                assertThat(context).hasNotFailed();
                var google = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("google");
                assertThat(google.getClientId()).isEqualTo("placeholder-id.apps.example.test");
                assertThat(google.getRedirectUri())
                    .isEqualTo("http://localhost:8080/login/oauth2/code/google");
            });
    }
}
