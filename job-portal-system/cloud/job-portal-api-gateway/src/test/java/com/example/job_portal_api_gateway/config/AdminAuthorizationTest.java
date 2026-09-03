package com.example.job_portal_api_gateway.config;

import com.example.job_portal_api_gateway.jwt.JwtConstant;
import com.example.job_portal_api_gateway.jwt.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuthorizationTest {
    static Stream<String> restrictedEndpoints() {
        var endpoints = List.of(
                "GET /api/users", "HEAD /api/users", "GET /api/users/", "GET /api/users?page=1",
                "PATCH /api/users/42/suspend", "PATCH /api/users/42/activate",
                "DELETE /api/users/42", "PATCH /api/companies/42/verify",
                "PATCH /api/companies/42/deactivate");
        var metadata = Stream.of("job-categories", "job-skills", "job-tags")
                .flatMap(resource -> Stream.of("POST /api/" + resource,
                        "PUT /api/" + resource + "/42", "DELETE /api/" + resource + "/42"));
        return Stream.concat(endpoints.stream(), metadata);
    }

    static Stream<Arguments> restrictedRequests() {
        return restrictedEndpoints().flatMap(endpoint ->
                Stream.of("ROLE_JOB_SEEKER", "ROLE_EMPLOYER", "ROLE_ADMINISTRATOR", "")
                        .map(role -> Arguments.of(endpoint, role)));
    }

    @ParameterizedTest
    @MethodSource("restrictedRequests")
    void rejectsNonAdminsEvenWithSpoofedAdminHeader(String endpoint, String role) {
        var error = assertThrows(ResponseStatusException.class,
                () -> new RouteConfig(new JwtUtil()).jwtAuthFilter(request(endpoint, role)));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @ParameterizedTest
    @MethodSource("restrictedEndpoints")
    void adminsRetainAccess(String endpoint) {
        var filtered = new RouteConfig(new JwtUtil()).jwtAuthFilter(request(endpoint, "ROLE_ADMIN"));
        assertEquals("ROLE_ADMIN", filtered.headers().firstHeader("X-User-Role"));
    }

    static Stream<Arguments> ordinaryRequests() {
        return Stream.of("ROLE_JOB_SEEKER", "ROLE_EMPLOYER").flatMap(role -> Stream.of(
                "GET /api/users/profile", "PUT /api/users/profile", "GET /api/users/42",
                "GET /api/job-categories", "GET /api/job-categories/42",
                "GET /api/job-skills", "GET /api/job-skills/42",
                "GET /api/job-tags", "GET /api/job-tags/42",
                "POST /api/jobs", "PUT /api/jobs/42", "DELETE /api/jobs/42")
                .map(endpoint -> Arguments.of(endpoint, role)));
    }

    @ParameterizedTest
    @MethodSource("ordinaryRequests")
    void preservesAuthenticatedNonAdminRoutes(String endpoint, String role) {
        var filtered = new RouteConfig(new JwtUtil()).jwtAuthFilter(request(endpoint, role));
        assertEquals(role, filtered.headers().firstHeader("X-User-Role"));
        assertEquals("42", filtered.headers().firstHeader("X-User-Id"));
    }

    private static ServerRequest request(String endpoint, String role) {
        String[] parts = endpoint.split(" ", 2);
        String[] uri = parts[1].split("\\?", 2);
        var servletRequest = new MockHttpServletRequest(parts[0], uri[0]);
        if (uri.length > 1) servletRequest.setQueryString(uri[1]);
        String token = Jwts.builder().claim("email", "caller@example.com")
                .claim("authorities", role).claim("userId", 42L)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(JwtConstant.SECRET_KEY.getBytes())).compact();
        servletRequest.addHeader("Authorization", "Bearer " + token);
        servletRequest.addHeader("X-User-Role", "ROLE_ADMIN");
        return ServerRequest.create(servletRequest, List.of());
    }
}
