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
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceClientTest {

    @Mock
    private HttpClient httpClient;

    private ObjectMapper objectMapper;
    private IdentityServiceClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        String testSecret = Base64.getEncoder().encodeToString("a-very-long-test-secret-key-32-bytes-minimum!".getBytes());
        client = new IdentityServiceClient(httpClient, objectMapper, "http://localhost:8081", testSecret);
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
    void findActiveUserIdsByRole_followsPagesAndAsksForActiveUsersOnly() throws Exception {
        HttpResponse<String> first = mock(HttpResponse.class);
        when(first.statusCode()).thenReturn(200);
        when(first.body()).thenReturn("{\"content\":[{\"id\":7},{\"id\":8}],\"last\":false}");
        HttpResponse<String> second = mock(HttpResponse.class);
        when(second.statusCode()).thenReturn(200);
        when(second.body()).thenReturn("{\"content\":[{\"id\":9}],\"last\":true}");
        java.util.List<java.net.URI> asked = new java.util.ArrayList<>();
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            asked.add(request.uri());
            return asked.size() == 1 ? first : second;
        });

        assertThat(client.findActiveUserIdsByRole("DISPATCHER")).containsExactly("7", "8", "9");
        assertThat(asked).hasSize(2);
        assertThat(asked.get(0).getQuery()).contains("role=DISPATCHER").contains("status=ACTIVE").contains("page=0");
        assertThat(asked.get(1).getQuery()).contains("page=1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findActiveUserIdsByRole_whenIdentityDown_returnsEmpty() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("connection refused"));

        assertThat(client.findActiveUserIdsByRole("DISPATCHER")).isEmpty();
    }
}
