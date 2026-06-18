package dev.pedrohfreitas.api_gateway.repository;

import dev.pedrohfreitas.api_gateway.entity.RouteConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteConfigRepository extends JpaRepository<RouteConfig, Long> {

    /** All enabled routes ordered by priority (highest first) for request matching */
    List<RouteConfig> findByEnabledTrueOrderByPriorityDesc();

    /** Check for conflicting route definitions */
    boolean existsByPathAndHttpMethodsAndEnabledTrue(String path, String httpMethods);
}
