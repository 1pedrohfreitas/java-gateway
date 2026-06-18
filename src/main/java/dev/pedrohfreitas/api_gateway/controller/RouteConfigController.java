package dev.pedrohfreitas.api_gateway.controller;

import dev.pedrohfreitas.api_gateway.dto.RouteConfigRequest;
import dev.pedrohfreitas.api_gateway.dto.RouteConfigResponse;
import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import dev.pedrohfreitas.api_gateway.service.RouteConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gateway/routes")
@RequiredArgsConstructor
public class RouteConfigController {

    private final RouteConfigService service;

    @GetMapping
    public List<RouteConfigResponse> list() {
        return service.findAll().stream()
                .map(RouteConfigResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public RouteConfigResponse get(@PathVariable Long id) {
        return RouteConfigResponse.from(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<RouteConfigResponse> create(@Valid @RequestBody RouteConfigRequest request) {
        RouteConfig created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RouteConfigResponse.from(created));
    }

    @PutMapping("/{id}")
    public RouteConfigResponse update(@PathVariable Long id,
                                      @Valid @RequestBody RouteConfigRequest request) {
        return RouteConfigResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public RouteConfigResponse toggle(@PathVariable Long id,
                                      @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", true);
        return RouteConfigResponse.from(service.toggle(id, enabled));
    }

    @PostMapping("/refresh-cache")
    public ResponseEntity<Map<String, String>> refreshCache() {
        service.refreshCache();
        return ResponseEntity.ok(Map.of("status", "cache refreshed"));
    }
}
