package dev.pedrohfreitas.api_gateway.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ant-style path pattern, e.g. /api/users/&#42;&#42; */
    @Column(nullable = false)
    private String path;

    /** Target base URL, e.g. http://user-service:8080 */
    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    /** Comma-separated HTTP methods or * for all, e.g. "GET,POST" */
    @Column(name = "http_methods", nullable = false)
    private String httpMethods;

    /** Whether JWT validation is required for this route */
    @Column(name = "jwt_required", nullable = false)
    private boolean jwtRequired;

    /** Whether this route is active */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Higher priority routes are checked first (for path pattern matching) */
    @Builder.Default
    @Column(nullable = false)
    private int priority = 0;

    /**
     * If true, strip the route's path prefix from the request URI before
     * appending to the target URL. Example:
     *   route path = /api/users/&#42;&#42;
     *   request    = /api/users/123/orders
     *   target     = http://user-svc:8080/123/orders  (with strip=true)
     *   target     = http://user-svc:8080/api/users/123/orders (with strip=false)
     */
    @Builder.Default
    @Column(name = "strip_prefix", nullable = false)
    private boolean stripPrefix = true;

    /** Timeout in milliseconds for the proxied HTTP call */
    @Builder.Default
    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 30000;

    /**
     * JSON object of headers to ADD to the proxied request.
     * Example: {"X-Gateway-Token": "internal-token", "X-Forwarded-By": "api-gateway"}
     */
    @Column(name = "add_request_headers", columnDefinition = "TEXT")
    private String addRequestHeaders;

    /**
     * JSON array of header names to REMOVE from the proxied request.
     * Example: ["Cookie", "Authorization"]
     */
    @Column(name = "remove_request_headers", columnDefinition = "TEXT")
    private String removeRequestHeaders;

    /**
     * JSON object of headers to ADD to the response sent back to the client.
     */
    @Column(name = "add_response_headers", columnDefinition = "TEXT")
    private String addResponseHeaders;

    /**
     * JSON array of header names to REMOVE from the response sent back to the client.
     */
    @Column(name = "remove_response_headers", columnDefinition = "TEXT")
    private String removeResponseHeaders;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
