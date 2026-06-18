package dev.pedrohfreitas.api_gateway.service;

import dev.pedrohfreitas.api_gateway.dto.RouteConfigRequest;
import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import dev.pedrohfreitas.api_gateway.exception.DuplicateRouteException;
import dev.pedrohfreitas.api_gateway.exception.RouteNotFoundException;
import dev.pedrohfreitas.api_gateway.repository.RouteConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteConfigServiceTest {

    @Mock
    RouteConfigRepository repository;

    @InjectMocks
    RouteConfigService service;

    RouteConfig route1, route2, route3;

    @BeforeEach
    void setUp() {
        route1 = RouteConfig.builder()
                .id(1L).path("/api/users/**").targetUrl("http://user-svc:8080")
                .httpMethods("*").enabled(true).priority(10).stripPrefix(true)
                .build();
        route2 = RouteConfig.builder()
                .id(2L).path("/api/products/**").targetUrl("http://product-svc:8080")
                .httpMethods("GET,POST").enabled(true).priority(5).stripPrefix(true)
                .build();
        route3 = RouteConfig.builder()
                .id(3L).path("/api/secure/**").targetUrl("http://secure-svc:8080")
                .httpMethods("*").enabled(true).priority(20).stripPrefix(false)
                .jwtRequired(true)
                .build();
    }

    @Nested
    @DisplayName("Route Matching")
    class RouteMatching {

        @Test
        @DisplayName("Should match exact path with correct HTTP method")
        void shouldMatchExactPath() {
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(
                    List.of(route3, route1, route2));

            service.refreshCache();

            RouteConfig match = service.findMatchingRoute("/api/users/123", "GET");
            assertThat(match).isNotNull();
            assertThat(match.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should filter by HTTP method")
        void shouldFilterByHttpMethod() {
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(
                    List.of(route1, route2));

            service.refreshCache();

            // route1 accepts *, route2 accepts only GET,POST
            RouteConfig match = service.findMatchingRoute("/api/products/99", "DELETE");
            assertThat(match).isNull();
        }

        @Test
        @DisplayName("Should return null when no route matches")
        void shouldReturnNullOnNoMatch() {
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(
                    List.of(route1, route2));

            service.refreshCache();

            RouteConfig match = service.findMatchingRoute("/api/unknown", "GET");
            assertThat(match).isNull();
        }

        @Test
        @DisplayName("Should honor priority ordering")
        void shouldHonorPriority() {
            RouteConfig lowPrio = RouteConfig.builder()
                    .id(5L).path("/api/shared/**").targetUrl("http://low:8080")
                    .httpMethods("*").enabled(true).priority(1).build();
            RouteConfig highPrio = RouteConfig.builder()
                    .id(6L).path("/api/shared/**").targetUrl("http://high:8080")
                    .httpMethods("*").enabled(true).priority(100).build();

            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(
                    List.of(highPrio, lowPrio));

            service.refreshCache();

            RouteConfig match = service.findMatchingRoute("/api/shared/data", "GET");
            assertThat(match).isNotNull();
            assertThat(match.getId()).isEqualTo(6L);
        }

        @Test
        @DisplayName("Should detect JWT required for matching route")
        void shouldRequireJwt() {
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(List.of(route3, route1));

            service.refreshCache();

            assertThat(service.requiresJwt("/api/secure/data", "GET")).isTrue();
            assertThat(service.requiresJwt("/api/users/123", "GET")).isFalse();
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("Should create a route and refresh cache")
        void shouldCreateRoute() {
            RouteConfigRequest req = new RouteConfigRequest(
                    "/api/test/**", "http://test:8080", "*",
                    false, true, 0, true, 30000,
                    null, null, null, null
            );

            when(repository.existsByPathAndHttpMethodsAndEnabledTrue("/api/test/**", "*"))
                    .thenReturn(false);
            when(repository.save(any(RouteConfig.class)))
                    .thenAnswer(inv -> {
                        RouteConfig r = inv.getArgument(0);
                        r.setId(10L);
                        return r;
                    });
            when(repository.findByEnabledTrueOrderByPriorityDesc())
                    .thenReturn(List.of());

            RouteConfig created = service.create(req);

            assertThat(created.getId()).isEqualTo(10L);
            assertThat(created.getPath()).isEqualTo("/api/test/**");
            assertThat(created.getTargetUrl()).isEqualTo("http://test:8080");
        }

        @Test
        @DisplayName("Should throw DuplicateRouteException when route already exists")
        void shouldThrowDuplicate() {
            RouteConfigRequest req = new RouteConfigRequest(
                    "/api/test/**", "http://test:8080", "*",
                    false, true, 0, true, 30000,
                    null, null, null, null
            );

            when(repository.existsByPathAndHttpMethodsAndEnabledTrue("/api/test/**", "*"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(DuplicateRouteException.class)
                    .hasMessageContaining("/api/test/**");
        }

        @Test
        @DisplayName("Should update an existing route")
        void shouldUpdateRoute() {
            RouteConfigRequest req = new RouteConfigRequest(
                    "/api/updated/**", "http://updated:8080", "GET",
                    true, true, 15, false, 60000,
                    "{\"X-Custom\":\"val\"}",
                    "[\"X-Old\"]",
                    "{\"X-Response\":\"val\"}",
                    "[\"X-Old-Response\"]"
            );

            when(repository.findById(1L)).thenReturn(Optional.of(route1));
            when(repository.save(any(RouteConfig.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(List.of());

            RouteConfig updated = service.update(1L, req);

            assertThat(updated.getPath()).isEqualTo("/api/updated/**");
            assertThat(updated.getTargetUrl()).isEqualTo("http://updated:8080");
            assertThat(updated.isJwtRequired()).isTrue();
            assertThat(updated.getTimeoutMs()).isEqualTo(60000);
        }

        @Test
        @DisplayName("Should throw NotFound when updating nonexistent route")
        void shouldThrowOnUpdateMissing() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(999L, null))
                    .isInstanceOf(RouteNotFoundException.class);
        }

        @Test
        @DisplayName("Should delete a route")
        void shouldDeleteRoute() {
            when(repository.findById(1L)).thenReturn(Optional.of(route1));
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(List.of());

            service.delete(1L);

            verify(repository).delete(route1);
        }

        @Test
        @DisplayName("Should toggle route enabled status")
        void shouldToggleRoute() {
            when(repository.findById(1L)).thenReturn(Optional.of(route1));
            when(repository.save(any(RouteConfig.class))).thenAnswer(inv -> inv.getArgument(0));
            when(repository.findByEnabledTrueOrderByPriorityDesc()).thenReturn(List.of());

            RouteConfig toggled = service.toggle(1L, false);
            assertThat(toggled.isEnabled()).isFalse();
        }
    }
}
