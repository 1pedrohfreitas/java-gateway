package dev.pedrohfreitas.api_gateway.config;

import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import dev.pedrohfreitas.api_gateway.repository.RouteConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with sample routes when running in dev profile.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final RouteConfigRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("Routes already exist — skipping seed.");
            return;
        }

        log.info("Seeding sample routes for dev...");

        repository.save(RouteConfig.builder()
                .path("/api/public/**")
                .targetUrl("http://localhost:8081")
                .httpMethods("*")
                .jwtRequired(false)
                .enabled(true)
                .priority(10)
                .stripPrefix(true)
                .timeoutMs(30000)
                .addResponseHeaders("{\"X-Gateway\":\"api-gateway-dev\"}")
                .build());

        repository.save(RouteConfig.builder()
                .path("/api/secure/**")
                .targetUrl("http://localhost:8082")
                .httpMethods("*")
                .jwtRequired(true)
                .enabled(true)
                .priority(20)
                .stripPrefix(true)
                .timeoutMs(30000)
                .removeRequestHeaders("[\"X-Internal-Token\"]")
                .addRequestHeaders("{\"X-Gateway-Auth\":\"internal\"}")
                .build());

        repository.save(RouteConfig.builder()
                .path("/api/products/**")
                .targetUrl("http://localhost:8083")
                .httpMethods("GET,POST")
                .jwtRequired(false)
                .enabled(true)
                .priority(5)
                .stripPrefix(false)
                .timeoutMs(60000)
                .build());

        log.info("Seeded {} sample routes.", repository.count());
    }
}
