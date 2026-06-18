package dev.pedrohfreitas.api_gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import dev.pedrohfreitas.api_gateway.exception.ProxyException;
import dev.pedrohfreitas.api_gateway.exception.RouteNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayProxyService {

    private final RouteConfigService routeConfigService;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** HTTP headers that should NOT be forwarded to the target. */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "host", "connection", "keep-alive", "transfer-encoding",
            "te", "trailer", "upgrade", "proxy-authorization",
            "proxy-authenticate", "x-application-context"
    );

    /**
     * Main proxy entry point. Called by the catch-all controller.
     */
    public void proxy(HttpServletRequest request, HttpServletResponse response) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        RouteConfig route = routeConfigService.findMatchingRoute(path, method);
        if (route == null) {
            throw new RouteNotFoundException(path, method);
        }

        String targetUrl = buildTargetUrl(route, path);
        log.debug("Proxying {} {} -> {}", method, path, targetUrl);

        try {
            ResponseEntity<byte[]> proxyResponse = forwardRequest(request, route, targetUrl, method);
            writeResponse(response, proxyResponse, route);
        } catch (RouteNotFoundException | ProxyException e) {
            throw e;
        } catch (Exception e) {
            throw new ProxyException(targetUrl, e);
        }
    }

    /**
     * Build the full target URL from the route config and request path.
     */
    String buildTargetUrl(RouteConfig route, String requestPath) {
        String baseUrl = route.getTargetUrl().replaceAll("/$", "");

        if (route.isStripPrefix()) {
            String patternPrefix = extractPatternPrefix(route.getPath());
            String remainingPath = extractRemainingPath(patternPrefix, requestPath);
            return baseUrl + remainingPath;
        } else {
            return baseUrl + requestPath;
        }
    }

    /**
     * Extract the literal prefix of an Ant-style pattern (before wildcards).
     * "/api/users/**" -> "/api/users"
     */
    private String extractPatternPrefix(String pattern) {
        int wildcard = pattern.indexOf('*');
        if (wildcard > 0) return pattern.substring(0, wildcard).replaceAll("/$", "");
        int placeholder = pattern.indexOf('{');
        if (placeholder > 0) return pattern.substring(0, placeholder).replaceAll("/$", "");
        return pattern;
    }

    /**
     * Remove the prefix from the request path.
     * Prefix "/api/users", request "/api/users/123/orders" -> "/123/orders"
     */
    private String extractRemainingPath(String prefix, String requestPath) {
        if (requestPath.startsWith(prefix)) {
            String remaining = requestPath.substring(prefix.length());
            return remaining.isEmpty() ? "/" : remaining;
        }
        return requestPath;
    }

    /**
     * Forward the request to the target service using RestClient.
     */
    private ResponseEntity<byte[]> forwardRequest(
            HttpServletRequest request, RouteConfig route, String targetUrl, String method) {

        RestClient client = restClientBuilder
                .baseUrl(targetUrl)
                .build();

        RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method.toUpperCase()))
                .uri(URI.create(targetUrl))
                .headers(headers -> applyRequestHeaders(request, route, headers));

        // Attach body for methods that support it
        if (needsRequestBody(method)) {
            byte[] body = readRequestBody(request);
            spec.body(body.length > 0 ? body : new byte[0]);
        }

        return spec.retrieve()
                .toEntity(byte[].class);
    }

    /**
     * Copy and filter request headers for the proxied request.
     */
    private void applyRequestHeaders(HttpServletRequest request, RouteConfig route,
                                     HttpHeaders headers) {
        // Copy original request headers (excluding hop-by-hop and internal ones)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) continue;
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }

        // Apply route-specific header filters
        applyHeaderFilter(route.getRemoveRequestHeaders(), headers, true);
        applyHeaderFilter(route.getAddRequestHeaders(), headers, false);
    }

    /**
     * Write the proxied response back to the client.
     */
    private void writeResponse(HttpServletResponse response,
                               ResponseEntity<byte[]> proxyResponse,
                               RouteConfig route) throws IOException {
        // Status
        response.setStatus(proxyResponse.getStatusCode().value());

        // Copy response headers
        HttpHeaders responseHeaders = proxyResponse.getHeaders();
        responseHeaders.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase()) && values != null) {
                values.forEach(value -> response.addHeader(name, value));
            }
        });

        // Apply route-specific response header filters
        applyHeaderFilterToResponse(route.getRemoveResponseHeaders(), response, true);
        applyHeaderFilterToResponse(route.getAddResponseHeaders(), response, false);

        // Body
        byte[] body = proxyResponse.getBody();
        if (body != null && body.length > 0) {
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
        }
    }

    // ---- Header manipulation helpers ----

    private void applyHeaderFilter(String headerJson, HttpHeaders headers, boolean remove) {
        if (headerJson == null || headerJson.isBlank()) return;
        try {
            if (remove) {
                List<String> removeList = objectMapper.readValue(headerJson,
                        new TypeReference<List<String>>() {});
                removeList.forEach(headers::remove);
            } else {
                Map<String, String> addMap = objectMapper.readValue(headerJson,
                        new TypeReference<Map<String, String>>() {});
                addMap.forEach(headers::set);
            }
        } catch (Exception e) {
            log.warn("Failed to parse request header config: {}", headerJson, e);
        }
    }

    private void applyHeaderFilterToResponse(String headerJson,
                                             HttpServletResponse response, boolean remove) {
        if (headerJson == null || headerJson.isBlank()) return;
        try {
            if (remove) {
                List<String> removeList = objectMapper.readValue(headerJson,
                        new TypeReference<List<String>>() {});
                for (String h : removeList) {
                    response.setHeader(h, null);
                }
            } else {
                Map<String, String> addMap = objectMapper.readValue(headerJson,
                        new TypeReference<Map<String, String>>() {});
                addMap.forEach(response::setHeader);
            }
        } catch (Exception e) {
            log.warn("Failed to parse response header config: {}", headerJson, e);
        }
    }

    private boolean needsRequestBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private byte[] readRequestBody(HttpServletRequest request) {
        try {
            InputStream is = request.getInputStream();
            return StreamUtils.copyToByteArray(is);
        } catch (IOException e) {
            log.warn("Failed to read request body", e);
            return new byte[0];
        }
    }
}
