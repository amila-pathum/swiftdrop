package com.swiftdrop.gateway.error;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.ConnectException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UpstreamUnavailableExceptionHandler implements ErrorWebExceptionHandler {
    private static final byte[] RESPONSE_BODY =
        "{\"error\":\"Upstream service is unavailable\"}".getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(exception);
        }
        boolean unavailable = isConnectionFailure(exception);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        if (unavailable) {
            response.setStatusCode(HttpStatus.BAD_GATEWAY);
            body = RESPONSE_BODY;
        } else if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode().is4xxClientError()) {
            // For example 404 for a path no route matches: keep the status, hide details.
            response.setStatusCode(statusException.getStatusCode());
            body = (statusException.getStatusCode().value() == 404
                ? "{\"error\":\"Not found\"}" : "{\"error\":\"Bad request\"}")
                .getBytes(StandardCharsets.UTF_8);
        } else {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            body = "{\"error\":\"Gateway request failed\"}".getBytes(StandardCharsets.UTF_8);
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private boolean isConnectionFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof WebClientRequestException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
