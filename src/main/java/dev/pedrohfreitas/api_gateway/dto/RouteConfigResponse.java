package dev.pedrohfreitas.api_gateway.dto;

import dev.pedrohfreitas.api_gateway.entity.RouteConfig;

import java.time.LocalDateTime;

public record RouteConfigResponse(
        Long id,
        String path,
        String targetUrl,
        String httpMethods,
        boolean jwtRequired,
        boolean enabled,
        int priority,
        boolean stripPrefix,
        int timeoutMs,
        String addRequestHeaders,
        String removeRequestHeaders,
        String addResponseHeaders,
        String removeResponseHeaders,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RouteConfigResponse from(RouteConfig r) {
        return new RouteConfigResponse(
                r.getId(), r.getPath(), r.getTargetUrl(), r.getHttpMethods(),
                r.isJwtRequired(), r.isEnabled(), r.getPriority(), r.isStripPrefix(),
                r.getTimeoutMs(),
                r.getAddRequestHeaders(), r.getRemoveRequestHeaders(),
                r.getAddResponseHeaders(), r.getRemoveResponseHeaders(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
