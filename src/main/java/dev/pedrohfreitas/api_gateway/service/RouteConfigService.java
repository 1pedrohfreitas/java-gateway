package dev.pedrohfreitas.api_gateway.service;

import dev.pedrohfreitas.api_gateway.dto.RouteConfigRequest;
import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import dev.pedrohfreitas.api_gateway.exception.DuplicateRouteException;
import dev.pedrohfreitas.api_gateway.exception.RouteNotFoundException;
import dev.pedrohfreitas.api_gateway.repository.RouteConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteConfigService {

    private final RouteConfigRepository repository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Thread-safe cache of enabled routes, ordered by priority descending. */
    private final List<RouteConfig> routeCache = new CopyOnWriteArrayList<>();

    /** Cache of already-matched paths to avoid re-scanning. */
    private final Map<String, RouteConfig> matchCache = new ConcurrentHashMap<>();

    @PostConstruct
    void loadCache() {
        refreshCache();
        log.info("Route cache loaded with {} enabled routes", routeCache.size());
    }

    /** Refresh the in-memory route cache from the database. */
    public void refreshCache() {
        List<RouteConfig> routes = repository.findByEnabledTrueOrderByPriorityDesc();
        routeCache.clear();
        routeCache.addAll(routes);
        matchCache.clear();
    }

    /**
     * Find the best-matching route for a request path and HTTP method.
     * Routes are checked in priority order (highest first).
     * Returns null if no route matches.
     */
    public RouteConfig findMatchingRoute(String requestPath, String httpMethod) {
        String cacheKey = httpMethod + ":" + requestPath;
        RouteConfig cached = matchCache.get(cacheKey);
        if (cached != null) return cached;

        for (RouteConfig route : routeCache) {
            if (!matchesMethod(route.getHttpMethods(), httpMethod)) continue;
            if (pathMatcher.match(route.getPath(), requestPath)) {
                matchCache.put(cacheKey, route);
                return route;
            }
        }
        return null;
    }

    /**
     * Check if a path requires JWT validation (used by the filter).
     */
    public boolean requiresJwt(String requestPath, String httpMethod) {
        RouteConfig route = findMatchingRoute(requestPath, httpMethod);
        return route != null && route.isJwtRequired();
    }

    private boolean matchesMethod(String routeMethods, String requestMethod) {
        if ("*".equals(routeMethods)) return true;
        for (String m : routeMethods.split(",")) {
            if (m.trim().equalsIgnoreCase(requestMethod)) return true;
        }
        return false;
    }

    // ---- CRUD ----

    public List<RouteConfig> findAll() {
        return repository.findAll();
    }

    public RouteConfig findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Route not found: id=" + id, "N/A"));
    }

    @Transactional
    public RouteConfig create(RouteConfigRequest req) {
        if (repository.existsByPathAndHttpMethodsAndEnabledTrue(req.path(), req.httpMethods())) {
            throw new DuplicateRouteException(req.path(), req.httpMethods());
        }
        RouteConfig entity = toEntity(req);
        RouteConfig saved = repository.save(entity);
        refreshCache();
        log.info("Route created: id={} {} {} -> {}",
                saved.getId(), saved.getHttpMethods(), saved.getPath(), saved.getTargetUrl());
        return saved;
    }

    @Transactional
    public RouteConfig update(Long id, RouteConfigRequest req) {
        RouteConfig existing = findById(id);
        applyRequest(existing, req);
        RouteConfig saved = repository.save(existing);
        refreshCache();
        log.info("Route updated: id={} {} {} -> {}",
                saved.getId(), saved.getHttpMethods(), saved.getPath(), saved.getTargetUrl());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        RouteConfig existing = findById(id);
        repository.delete(existing);
        refreshCache();
        log.info("Route deleted: id={} {} {}", id, existing.getHttpMethods(), existing.getPath());
    }

    @Transactional
    public RouteConfig toggle(Long id, boolean enabled) {
        RouteConfig route = findById(id);
        route.setEnabled(enabled);
        RouteConfig saved = repository.save(route);
        refreshCache();
        log.info("Route id={} {}", id, enabled ? "enabled" : "disabled");
        return saved;
    }

    // ---- Helpers ----

    private RouteConfig toEntity(RouteConfigRequest req) {
        return RouteConfig.builder()
                .path(req.path())
                .targetUrl(req.targetUrl())
                .httpMethods(req.httpMethods())
                .jwtRequired(req.jwtRequired())
                .enabled(req.enabled())
                .priority(req.priority())
                .stripPrefix(req.stripPrefix())
                .timeoutMs(req.timeoutMs() > 0 ? req.timeoutMs() : 30000)
                .addRequestHeaders(req.addRequestHeaders())
                .removeRequestHeaders(req.removeRequestHeaders())
                .addResponseHeaders(req.addResponseHeaders())
                .removeResponseHeaders(req.removeResponseHeaders())
                .build();
    }

    private void applyRequest(RouteConfig entity, RouteConfigRequest req) {
        entity.setPath(req.path());
        entity.setTargetUrl(req.targetUrl());
        entity.setHttpMethods(req.httpMethods());
        entity.setJwtRequired(req.jwtRequired());
        entity.setEnabled(req.enabled());
        entity.setPriority(req.priority());
        entity.setStripPrefix(req.stripPrefix());
        entity.setTimeoutMs(req.timeoutMs() > 0 ? req.timeoutMs() : 30000);
        entity.setAddRequestHeaders(req.addRequestHeaders());
        entity.setRemoveRequestHeaders(req.removeRequestHeaders());
        entity.setAddResponseHeaders(req.addResponseHeaders());
        entity.setRemoveResponseHeaders(req.removeResponseHeaders());
    }
}
