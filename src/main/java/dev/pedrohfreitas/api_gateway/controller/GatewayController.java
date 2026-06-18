package dev.pedrohfreitas.api_gateway.controller;

import dev.pedrohfreitas.api_gateway.service.GatewayProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catch-all controller for proxying requests.
 * Spring routes explicit paths (like /api/gateway/routes) with higher
 * priority than wildcard patterns, so the management endpoints
 * in RouteConfigController take precedence over this catch-all.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayProxyService proxyService;

    /**
     * Catch-all: any request that doesn't match a more specific controller
     * lands here. We check if it's a gateway-admin request and reject it;
     * otherwise proxy it through the configured routes.
     */
    @RequestMapping("/**")
    public void handle(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();

        // Let gateway-admin paths pass through (they're handled by specific controllers)
        if (path.startsWith("/api/gateway/")) {
            response.setStatus(404);
            return;
        }

        // Let actuator endpoints pass through
        if (path.startsWith("/actuator/")) {
            response.setStatus(404);
            return;
        }

        proxyService.proxy(request, response);
    }
}
