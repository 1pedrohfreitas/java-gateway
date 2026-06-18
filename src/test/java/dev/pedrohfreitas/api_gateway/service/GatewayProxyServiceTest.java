package dev.pedrohfreitas.api_gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GatewayProxyServiceTest {

    MockWebServer mockBackend;
    GatewayProxyService proxyService;
    RouteConfigService routeConfigService;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockBackend = new MockWebServer();
        mockBackend.start();

        routeConfigService = mock(RouteConfigService.class);
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();

        proxyService = new GatewayProxyService(routeConfigService, builder, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockBackend.shutdown();
    }

    @Test
    @DisplayName("Should build correct target URL with stripPrefix=true")
    void shouldBuildTargetUrlWithStripPrefix() {
        RouteConfig route = RouteConfig.builder()
                .path("/api/users/**")
                .targetUrl("http://localhost:" + mockBackend.getPort())
                .httpMethods("*")
                .enabled(true)
                .stripPrefix(true)
                .build();

        String url = proxyService.buildTargetUrl(route, "/api/users/123/orders");
        assertThat(url).isEqualTo("http://localhost:" + mockBackend.getPort() + "/123/orders");
    }

    @Test
    @DisplayName("Should build correct target URL with stripPrefix=false")
    void shouldBuildTargetUrlWithoutStripPrefix() {
        RouteConfig route = RouteConfig.builder()
                .path("/api/users/**")
                .targetUrl("http://localhost:" + mockBackend.getPort())
                .httpMethods("*")
                .enabled(true)
                .stripPrefix(false)
                .build();

        String url = proxyService.buildTargetUrl(route, "/api/users/123/orders");
        assertThat(url).isEqualTo("http://localhost:" + mockBackend.getPort() + "/api/users/123/orders");
    }

    @Test
    @DisplayName("Should strip trailing slash from target URL")
    void shouldStripTrailingSlash() {
        RouteConfig route = RouteConfig.builder()
                .path("/api/**")
                .targetUrl("http://localhost:" + mockBackend.getPort() + "/")
                .httpMethods("*")
                .enabled(true)
                .stripPrefix(true)
                .build();

        String url = proxyService.buildTargetUrl(route, "/api/test");
        assertThat(url).isEqualTo("http://localhost:" + mockBackend.getPort() + "/test");
    }

    @Test
    @DisplayName("Should proxy GET request to backend")
    void shouldProxyGetRequest() throws Exception {
        mockBackend.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"message\":\"ok\"}")
                .setHeader("Content-Type", "application/json"));

        RouteConfig route = RouteConfig.builder()
                .path("/api/test/**")
                .targetUrl("http://localhost:" + mockBackend.getPort())
                .httpMethods("*")
                .enabled(true)
                .stripPrefix(true)
                .build();

        when(routeConfigService.findMatchingRoute("/api/test/data", "GET")).thenReturn(route);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test/data");
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) { outputStream.write(b); }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener l) {}
        });

        proxyService.proxy(request, response);

        verify(response).setStatus(200);
        assertThat(outputStream.toString()).contains("ok");
    }

    @Test
    @DisplayName("Should return empty body for GET without content")
    void shouldHandleEmptyGetResponse() throws Exception {
        mockBackend.enqueue(new MockResponse()
                .setResponseCode(204)
                .setBody(""));

        RouteConfig route = RouteConfig.builder()
                .path("/api/empty/**")
                .targetUrl("http://localhost:" + mockBackend.getPort())
                .httpMethods("*")
                .enabled(true)
                .stripPrefix(true)
                .build();

        when(routeConfigService.findMatchingRoute("/api/empty/test", "GET")).thenReturn(route);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/empty/test");
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int b) { outputStream.write(b); }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener l) {}
        });

        proxyService.proxy(request, response);

        verify(response).setStatus(204);
    }
}
