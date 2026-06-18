package dev.pedrohfreitas.api_gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

public record RouteConfigRequest(
        @NotBlank(message = "path is required")
        String path,

        @NotBlank(message = "targetUrl is required")
        String targetUrl,

        @NotBlank(message = "httpMethods is required")
        String httpMethods,

        boolean jwtRequired,

        boolean enabled,

        @PositiveOrZero
        int priority,

        boolean stripPrefix,

        @Positive
        int timeoutMs,

        String addRequestHeaders,
        String removeRequestHeaders,
        String addResponseHeaders,
        String removeResponseHeaders
) {}
