package dev.pedrohfreitas.api_gateway.exception;

import org.springframework.http.HttpStatus;

/** Thrown when JWT validation fails. */
public class JwtValidationException extends GatewayException {

    public JwtValidationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    public JwtValidationException(String message, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED, cause);
    }
}
