package com.swiftdrop.notification.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceClientTest {

    @Mock
    private HttpClient httpClient;

    private static final String TOKEN = "0123456789abcdef0123456789abcdef-internal";
    private ObjectMapper objectMapper;
    private IdentityServiceClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        client = new IdentityServiceClient(httpClient, objectMapper, "http://localhost:8081", TOKEN);
    }

    @Test
    void findEmailByCustomerId_whenDirectEmail_returnsImmediately() {
        Optional<String> email = client.findEmailByCustomerId("customer@example.com");
        assertThat(email).contains("customer@example.com");
    }

    @Test
    void findEmailByCustomerId_whenBlank_returnsEmpty() {
        assertThat(client.findEmailByCustomerId("")).isEmpty();
        assertThat(client.findEmailByCustomerId(null)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findEmailByCustomerId_whenNumeric_queriesIdentityService() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":42,\"email\":\"john.doe@example.com\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        Optional<String> email = client.findEmailByCustomerId("42");
        assertThat(email).contains("john.doe@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findActiveUserIdsByRole_usesTheInternalApiWithTheServiceToken() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[7,8,9]");
        java.util.List<HttpRequest> asked = new java.util.ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            asked.add(invocation.getArgument(0));
            return response;
        });

        assertThat(client.findActiveUserIdsByRole("DISPATCHER")).containsExactly("7", "8", "9");
        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).uri().getPath()).isEqualTo("/internal/users");
        assertThat(asked.get(0).uri().getQuery()).contains("role=DISPATCHER").contains("status=ACTIVE");
        assertThat(asked.get(0).headers().firstValue(IdentityServiceClient.TOKEN_HEADER)).contains(TOKEN);
        assertThat(asked.get(0).headers().firstValue("Authorization")).isEmpty();
    }

    @Test
    void findEmailByCustomerId_withoutAServiceToken_skipsTheLookup() throws Exception {
        var unconfigured = new IdentityServiceClient(httpClient, objectMapper, "http://localhost:8081", "");
        assertThat(unconfigured.findEmailByCustomerId("42")).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(httpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findActiveUserIdsByRole_whenIdentityDown_returnsEmpty() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        assertThat(client.findActiveUserIdsByRole("DISPATCHER")).isEmpty();
    }
}
