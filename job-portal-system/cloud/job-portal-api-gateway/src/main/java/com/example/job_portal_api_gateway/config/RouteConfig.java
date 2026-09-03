package com.example.job_portal_api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.*;

import com.example.job_portal_api_gateway.jwt.JwtConstant;
import com.example.job_portal_api_gateway.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import java.util.Arrays;

@Configuration
public class RouteConfig {

    private final JwtUtil jwtUtil;

    public RouteConfig(JwtUtil jwtUtil){
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public RouterFunction<ServerResponse> authRoutes(){
        return GatewayRouterFunctions.route("auth-routes")
                .route(RequestPredicates.path("/auth/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-user-service"))
                .build();
    }

    // ==================== Admin-only Routes (JWT + ROLE_ADMIN) ====================

    @Bean
    public RouterFunction<ServerResponse> adminRoutes() {
        return GatewayRouterFunctions.route("admin-routes")
                .route(RequestPredicates.path("/api/admin/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-user-service"))
                .before(this::jwtAuthFilter)
                .before(request -> requireRole(request, "ROLE_ADMIN"))
                .build();
    }

    private ServerRequest requireRole(ServerRequest request, String roleAdmin) {
        String roles = request.headers().firstHeader("X-User-Role");
        if (!hasRole(roles, roleAdmin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied for role " + roleAdmin);
        }
        return request;
    }

    private boolean hasRole(String roles, String requiredRole) {
        return roles != null && Arrays.stream(roles.split(","))
                .anyMatch(requiredRole::equals);
    }

    // ==================== Protected Routes (JWT required) ====================

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes() {
        return GatewayRouterFunctions.route("user-service-routes")
                .route(RequestPredicates.path("/api/users/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-user-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> companyServiceRoutes() {
        return GatewayRouterFunctions.route("company-service-routes")
                .route(RequestPredicates.path("/api/companies/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-company-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> jobServiceRoutes() {
        return GatewayRouterFunctions.route("job-service-routes")
                .route(RequestPredicates.path("/api/jobs/**")
                                .or(RequestPredicates.path("/api/job-categories/**"))
                                .or(RequestPredicates.path("/api/job-skills/**"))
                                .or(RequestPredicates.path("/api/job-tags/**")),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-job-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> applicationServiceRoutes() {
        return GatewayRouterFunctions.route("application-service-routes")
                .route(RequestPredicates.path("/api/applications/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-application-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> resumeServiceRoutes() {
        return GatewayRouterFunctions.route("resume-service-routes")
                .route(RequestPredicates.path("/api/resumes/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-resume-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> preferenceServiceRoutes() {
        return GatewayRouterFunctions.route("preference-service-routes")
                .route(RequestPredicates.path("/api/preferences/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-preference-service"))
                .before(this::jwtAuthFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> aiServiceRoutes() {
        return GatewayRouterFunctions.route("ai-service-routes")
                .route(RequestPredicates.path("/api/ai/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("job-portal-ai-service"))
                .before(this::jwtAuthFilter)
                .build();
    }


//    jwt filter
    ServerRequest jwtAuthFilter(ServerRequest request){
        String authHeader = request.headers().firstHeader(JwtConstant.JWT_HEADER);

        if (authHeader == null || !authHeader.startsWith(JwtConstant.TOKEN_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid authorization header");
        }

//        Bearer token
        String token = authHeader.substring(JwtConstant.TOKEN_PREFIX.length());

        Claims claims;
        try {
            claims = jwtUtil.extractAllClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired JWT token");
        }

        String email = claims.get("email", String.class);
        String authorities = claims.get("authorities", String.class);
        Long userId = claims.get("userId", Long.class);

        if (requiresAdmin(request) && !hasRole(authorities, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This operation requires an admin");
        }

        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.set("X-User-Id", String.valueOf(userId));
                    headers.set("X-User-Email", email);
                    headers.set("X-User-Role", authorities);
                })
                .build();
    }

    private boolean requiresAdmin(ServerRequest request) {
        HttpMethod method = request.method();
        // Use the same parsed path matching as the gateway routes, including path parameters.
        if (RequestPredicates.path("/api/admin/**").test(request)) {
            return true;
        }
        if ((method == HttpMethod.GET || method == HttpMethod.HEAD)
                && (RequestPredicates.path("/api/users").test(request)
                    || RequestPredicates.path("/api/users/").test(request))) {
            return true;
        }
        if ((method == HttpMethod.DELETE && RequestPredicates.path("/api/users/{id}").test(request))
                || (method == HttpMethod.PATCH
                    && (RequestPredicates.path("/api/users/{id}/suspend").test(request)
                        || RequestPredicates.path("/api/users/{id}/activate").test(request)
                        || RequestPredicates.path("/api/companies/{id}/verify").test(request)
                        || RequestPredicates.path("/api/companies/{id}/deactivate").test(request)))) {
            return true;
        }
        return method != HttpMethod.GET && method != HttpMethod.HEAD && method != HttpMethod.OPTIONS
                && (RequestPredicates.path("/api/job-categories/**").test(request)
                    || RequestPredicates.path("/api/job-skills/**").test(request)
                    || RequestPredicates.path("/api/job-tags/**").test(request));
    }
}
