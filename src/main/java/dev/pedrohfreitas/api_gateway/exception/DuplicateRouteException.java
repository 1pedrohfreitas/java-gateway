package dev.pedrohfreitas.api_gateway.exception;

import org.springframework.http.HttpStatus;

/** Thrown when attempting to create a route that already exists. */
public class DuplicateRouteException extends GatewayException {

    public DuplicateRouteException(String path, String methods) {
        super("Route already exists for " + methods + " " + path, HttpStatus.CONFLICT);
    }
}
