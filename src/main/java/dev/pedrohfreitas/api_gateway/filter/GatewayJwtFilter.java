package dev.pedrohfreitas.api_gateway.filter;

import dev.pedrohfreitas.api_gateway.security.JwtService;
import dev.pedrohfreitas.api_gateway.service.RouteConfigService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that intercepts requests and validates JWT tokens for routes
 * that require authentication. Runs before the catch-all gateway controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayJwtFilter extends OncePerRequestFilter {

    private final RouteConfigService routeConfigService;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip JWT check for gateway admin endpoints
        if (path.startsWith("/api/gateway/") || path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if this path requires JWT
        if (!routeConfigService.requiresJwt(path, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate JWT
        try {
            String authHeader = request.getHeader("Authorization");
            Claims claims = jwtService.validateFromAuthorizationHeader(authHeader);

            // Optionally set attributes for downstream use
            request.setAttribute("jwt.subject", claims.getSubject());
            request.setAttribute("jwt.claims", claims);

            log.debug("JWT validated for {} {}: subject={}", method, path, claims.getSubject());
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("JWT validation failed for {} {}: {}", method, path, e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":\"%s\"}",
                    e.getMessage(), path, java.time.LocalDateTime.now().toString()
            ));
        }
    }
}
