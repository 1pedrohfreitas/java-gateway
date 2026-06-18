package dev.pedrohfreitas.api_gateway.exception;

import org.springframework.http.HttpStatus;

/** Thrown when proxying to the target service fails. */
public class ProxyException extends GatewayException {

    public ProxyException(String targetUrl, Throwable cause) {
        super("Failed to proxy request to " + targetUrl + ": " + cause.getMessage(),
                HttpStatus.BAD_GATEWAY, cause);
    }
}
