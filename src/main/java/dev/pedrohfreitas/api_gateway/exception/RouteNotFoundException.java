package dev.pedrohfreitas.api_gateway.exception;

import org.springframework.http.HttpStatus;

/** Thrown when no matching route is found for an incoming request. */
public class RouteNotFoundException extends GatewayException {

    public RouteNotFoundException(String path, String method) {
        super("No route found for " + method + " " + path, HttpStatus.NOT_FOUND);
    }
}
