package com.swiftdrop.gateway.error;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
        response.setStatusCode(unavailable
            ? HttpStatus.BAD_GATEWAY : HttpStatus.INTERNAL_SERVER_ERROR);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = unavailable ? RESPONSE_BODY
            : "{\"error\":\"Gateway request failed\"}".getBytes(StandardCharsets.UTF_8);
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
