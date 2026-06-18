package dev.pedrohfreitas.api_gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pedrohfreitas.api_gateway.dto.RouteConfigRequest;
import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import dev.pedrohfreitas.api_gateway.repository.RouteConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("dev")
class RouteConfigControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    RouteConfigRepository repository;

    @Autowired
    ObjectMapper objectMapper;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        repository.deleteAll();
        repository.flush();
    }

    @Test
    @DisplayName("POST /api/gateway/routes — should create a route and return 201")
    void shouldCreateRoute() throws Exception {
        RouteConfigRequest req = new RouteConfigRequest(
                "/api/test/**", "http://test:8080", "GET",
                false, true, 0, true, 30000,
                null, null, null, null
        );

        mockMvc.perform(post("/api/gateway/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.path").value("/api/test/**"))
                .andExpect(jsonPath("$.targetUrl").value("http://test:8080"));
    }

    @Test
    @DisplayName("GET /api/gateway/routes — should list all routes")
    void shouldListRoutes() throws Exception {
        repository.save(RouteConfig.builder()
                .path("/api/a/**").targetUrl("http://a:8080")
                .httpMethods("*").enabled(true).priority(1).timeoutMs(30000)
                .build());
        repository.save(RouteConfig.builder()
                .path("/api/b/**").targetUrl("http://b:8080")
                .httpMethods("GET").enabled(true).priority(2).timeoutMs(30000)
                .build());

        mockMvc.perform(get("/api/gateway/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/gateway/routes/{id} — should return a single route")
    void shouldGetRoute() throws Exception {
        RouteConfig saved = repository.save(RouteConfig.builder()
                .path("/api/single/**").targetUrl("http://single:8080")
                .httpMethods("POST").enabled(true).priority(5).timeoutMs(30000)
                .build());

        mockMvc.perform(get("/api/gateway/routes/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().intValue()))
                .andExpect(jsonPath("$.path").value("/api/single/**"));
    }

    @Test
    @DisplayName("PUT /api/gateway/routes/{id} — should update a route")
    void shouldUpdateRoute() throws Exception {
        RouteConfig saved = repository.save(RouteConfig.builder()
                .path("/api/old/**").targetUrl("http://old:8080")
                .httpMethods("*").enabled(true).priority(1).timeoutMs(30000)
                .build());

        RouteConfigRequest update = new RouteConfigRequest(
                "/api/new/**", "http://new:8080", "GET,POST",
                true, true, 10, false, 60000,
                "{\"X-Custom\":\"val\"}",
                "[\"X-Remove\"]",
                null, null
        );

        mockMvc.perform(put("/api/gateway/routes/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/api/new/**"))
                .andExpect(jsonPath("$.targetUrl").value("http://new:8080"))
                .andExpect(jsonPath("$.jwtRequired").value(true));
    }

    @Test
    @DisplayName("DELETE /api/gateway/routes/{id} — should delete a route")
    void shouldDeleteRoute() throws Exception {
        RouteConfig saved = repository.save(RouteConfig.builder()
                .path("/api/delete/**").targetUrl("http://delete:8080")
                .httpMethods("*").enabled(true).priority(1).timeoutMs(30000)
                .build());

        mockMvc.perform(delete("/api/gateway/routes/" + saved.getId()))
                .andExpect(status().isNoContent());

        assert repository.findById(saved.getId()).isEmpty();
    }

    @Test
    @DisplayName("PATCH /api/gateway/routes/{id}/toggle — should toggle enabled status")
    void shouldToggleRoute() throws Exception {
        RouteConfig saved = repository.save(RouteConfig.builder()
                .path("/api/toggle/**").targetUrl("http://toggle:8080")
                .httpMethods("*").enabled(true).priority(1).timeoutMs(30000)
                .build());

        mockMvc.perform(patch("/api/gateway/routes/" + saved.getId() + "/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("GET /api/gateway/routes/{id} — should return 404 for nonexistent route")
    void shouldReturn404ForMissingRoute() throws Exception {
        mockMvc.perform(get("/api/gateway/routes/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/gateway/routes — should return 400 on validation error")
    void shouldReturn400OnValidationError() throws Exception {
        // Send a request with blank required fields to trigger validation
        String invalidJson = """
                {
                    "path": "",
                    "targetUrl": "",
                    "httpMethods": "",
                    "jwtRequired": false,
                    "enabled": true,
                    "priority": 0,
                    "stripPrefix": true,
                    "timeoutMs": 30000
                }""";

        mockMvc.perform(post("/api/gateway/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
