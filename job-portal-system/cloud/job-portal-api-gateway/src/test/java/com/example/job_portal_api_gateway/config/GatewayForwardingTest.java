package com.example.job_portal_api_gateway.config;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import static org.junit.jupiter.api.Assertions.*;

class GatewayForwardingTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({GatewayHttpClientConfig.class, ShortlistAdmissionFilter.class})
    static class TestGateway {
        @Bean
        RouterFunction<ServerResponse> routes(Environment environment) {
            return GatewayRouterFunctions.route("test-backend")
                    .route(RequestPredicates.path("/api/applications/**"),
                            HandlerFunctions.http())
                    .before(org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri(environment.getRequiredProperty("test.backend")))
                    .build();
        }
    }

    @Test
    void forwardsAdmittedPatchRequestsRejectsOverflowAndReusesConnections() throws Exception {
        int concurrent = 8;
        var arrived = new CountDownLatch(concurrent);
        var release = new CountDownLatch(1);
        var backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var backendExecutor = Executors.newFixedThreadPool(concurrent);
        backend.setExecutor(backendExecutor);
        backend.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            arrived.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                byte[] body = "{\"status\":\"SHORTLISTED\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        backend.start();
        try (var context = SpringApplication.run(TestGateway.class,
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.config.location=optional:classpath:/forwarding-test-empty.properties",
                "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                "--spring.cloud.discovery.enabled=false", "--spring.cloud.compatibility-verifier.enabled=false",
                "--server.tomcat.threads.max=16", "--gateway.shortlist.max-in-flight=8",
                "--test.backend=http://127.0.0.1:" + backend.getAddress().getPort());
             var client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var requests = new ArrayList<CompletableFuture<HttpResponse<String>>>();
            try {
                for (int i = 0; i < concurrent; i++) requests.add(client.sendAsync(patch(port, i), HttpResponse.BodyHandlers.ofString()));
                assertTrue(arrived.await(4, TimeUnit.SECONDS),
                        "Every admitted PATCH must reach the real backend concurrently");
                var rejected = client.send(patch(port, 99), HttpResponse.BodyHandlers.ofString());
                assertEquals(503, rejected.statusCode());
                assertEquals("1", rejected.headers().firstValue("Retry-After").orElseThrow());
            } finally {
                release.countDown();
            }
            for (var request : requests) assertEquals(200, request.get(5, TimeUnit.SECONDS).statusCode());
            for (int i = 0; i < concurrent * 2; i++) {
                assertEquals(200, client.send(patch(port, i + 100), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
        } finally {
            release.countDown();
            backend.stop(0);
            backendExecutor.shutdownNow();
        }
    }

    @Test
    void stalledBackendReleasesAdmissionBeforeTheFiveSecondCallerDeadline() throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var backendExecutor = Executors.newFixedThreadPool(2);
        backend.setExecutor(backendExecutor);
        backend.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                if (exchange.getRequestURI().getPath().contains("/1/")) {
                    arrived.countDown();
                    release.await(10, TimeUnit.SECONDS);
                }
                byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        backend.start();
        try (var context = SpringApplication.run(TestGateway.class,
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                "--spring.cloud.discovery.enabled=false", "--spring.cloud.compatibility-verifier.enabled=false",
                "--gateway.shortlist.max-in-flight=1", "--spring.http.clients.read-timeout=2s",
                "--test.backend=http://127.0.0.1:" + backend.getAddress().getPort());
             var client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var stalled = client.sendAsync(patch(port, 1), HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(arrived.await(2, TimeUnit.SECONDS));
                assertEquals(503, client.send(patch(port, 3), HttpResponse.BodyHandlers.ofString()).statusCode());
                assertTrue(stalled.get(4, TimeUnit.SECONDS).statusCode() >= 500,
                        "Stalled forwarding must finish before the caller's five-second timeout");
                assertEquals(200, client.send(patch(port, 2), HttpResponse.BodyHandlers.ofString()).statusCode(),
                        "A healthy PATCH must recover the admission permit while the old backend remains stalled");
            } finally {
                release.countDown();
                stalled.get(6, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            backend.stop(0);
            backendExecutor.shutdownNow();
        }
    }
    private static HttpRequest patch(int port, int id) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/applications/" + id + "/status"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"status\":\"SHORTLISTED\"}")).build();
    }
}




