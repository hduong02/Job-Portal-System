package com.example.job_portal_api_gateway.config;

import com.example.job_portal_api_gateway.jwt.JwtConstant;
import com.example.job_portal_api_gateway.jwt.JwtUtil;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuthorizationHttpTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RouteConfig.class, JwtUtil.class})
    static class TestGateway {}

    @Test
    void actualRoutesRejectBeforeForwardingAndAllowAdminsAndOrdinaryReads() throws Exception {
        var forwarded = new AtomicInteger();
        var backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/", exchange -> {
            forwarded.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        backend.start();
        String backendUri = "http://127.0.0.1:" + backend.getAddress().getPort();
        try (var context = SpringApplication.run(TestGateway.class,
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                "--spring.cloud.compatibility-verifier.enabled=false",
                "--spring.cloud.discovery.client.simple.instances.job-portal-user-service[0].uri=" + backendUri,
                "--spring.cloud.discovery.client.simple.instances.job-portal-job-service[0].uri=" + backendUri);
             var client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var endpoints = AdminAuthorizationTest.restrictedEndpoints()
                    .filter(endpoint -> !endpoint.contains("companies")).toList();
            for (String role : List.of("ROLE_JOB_SEEKER", "ROLE_EMPLOYER")) {
                for (String endpoint : endpoints) {
                    var response = send(client, port, endpoint, role);
                    assertEquals(403, response.statusCode(), endpoint + " " + role);
                    assertFalse(response.body().contains("\"trace\""), response.body());
                }
            }
            assertEquals(0, forwarded.get(), "Rejected requests must never reach a service");
            for (String endpoint : endpoints) {
                assertEquals(204, send(client, port, endpoint, "ROLE_ADMIN").statusCode(), endpoint);
            }
            for (String endpoint : List.of("GET /api/users/profile", "GET /api/job-categories",
                    "GET /api/job-skills", "GET /api/job-tags")) {
                assertEquals(204, send(client, port, endpoint, "ROLE_JOB_SEEKER").statusCode(), endpoint);
            }
            assertEquals(endpoints.size() + 4, forwarded.get());
        } finally {
            backend.stop(0);
        }
    }

    private HttpResponse<String> send(HttpClient client, int port, String endpoint, String role) throws Exception {
        String[] parts = endpoint.split(" ", 2);
        String token = Jwts.builder().claim("email", "caller@example.com")
                .claim("authorities", role).claim("userId", 42L)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(JwtConstant.SECRET_KEY.getBytes())).compact();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + parts[1]))
                .header("Authorization", "Bearer " + token).header("X-User-Role", "ROLE_ADMIN")
                .method(parts[0], HttpRequest.BodyPublishers.noBody()).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
