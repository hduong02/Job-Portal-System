package com.example.job_portal_api_gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class GatewayHttpClientConfigTest {
    @Test
    void gatewayFactoryCanForwardMoreThanFiveRequestsToTheSameBackend() throws Exception {
        int concurrent = 8;
        var arrived = new CountDownLatch(concurrent);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newFixedThreadPool(concurrent);
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            arrived.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
                byte[] response = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class,
                    ImperativeHttpClientAutoConfiguration.class))
                .withUserConfiguration(GatewayHttpClientConfig.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("gateway.shortlist.max-in-flight=8")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var factory = context.getBean(ClientHttpRequestFactory.class);
                    var client = RestClient.builder().requestFactory(factory).build();
                    try (var executor = Executors.newFixedThreadPool(concurrent)) {
                        var requests = new ArrayList<java.util.concurrent.Future<String>>();
                        try {
                            for (int i = 0; i < concurrent; i++) {
                                requests.add(executor.submit(() -> client.get()
                                    .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                                    .retrieve().body(String.class)));
                            }
                            assertTrue(arrived.await(4, TimeUnit.SECONDS),
                                "All admitted requests must reach the backend concurrently instead of waiting behind a five-connection pool");
                        } finally {
                            release.countDown();
                        }
                        for (var request : requests) assertEquals("ok", request.get(5, TimeUnit.SECONDS));
                    }
                });
        } finally {
            release.countDown();
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void boundsPoolWaitingAndMeasuresAcquisitionSeparatelyFromBackendWork() throws Exception {
        var arrived = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newFixedThreadPool(2);
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            arrived.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class,
                            ImperativeHttpClientAutoConfiguration.class))
                    .withUserConfiguration(GatewayHttpClientConfig.class)
                    .withBean(MeterRegistry.class,
                            SimpleMeterRegistry::new)
                    .withPropertyValues("gateway.shortlist.max-in-flight=1",
                            "gateway.shortlist.connection-request-timeout-ms=120")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        var registry = context.getBean(MeterRegistry.class);
                        var client = RestClient.builder().requestFactory(context.getBean(ClientHttpRequestFactory.class)).build();
                        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/applications/1/status";
                        try (var executor = Executors.newSingleThreadExecutor()) {
                            var first = executor.submit(() -> client.patch().uri(url).body("{}").retrieve().body(String.class));
                            try {
                                assertTrue(arrived.await(3, TimeUnit.SECONDS));
                                long start = System.nanoTime();
                                assertThrows(org.springframework.web.client.ResourceAccessException.class,
                                        () -> client.patch().uri(url).body("{}").retrieve().body(String.class));
                                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                                assertTrue(elapsedMs < 1500, "Pool saturation must not wait for the caller's deadline");
                                var acquisition = registry.get("gateway.shortlist.connection.acquire").tag("outcome", "error").timer();
                                assertEquals(1, acquisition.count());
                                assertTrue(acquisition.totalTime(TimeUnit.MILLISECONDS) >= 80,
                                        "Acquisition timing must include the actual pool wait");
                                assertEquals(0, registry.get("gateway.shortlist.backend").tag("outcome", "error").timer().count(),
                                        "A pool timeout must not be mislabeled as backend execution");
                                assertEquals(0, registry.get("gateway.shortlist.connection.acquiring").longTaskTimer().activeTasks());
                            } finally {
                                release.countDown();
                            }
                            assertEquals("ok", first.get(3, TimeUnit.SECONDS));
                            assertEquals(1, registry.get("gateway.shortlist.connection.acquire").tag("outcome", "success").timer().count());
                            assertEquals(1, registry.get("gateway.shortlist.backend").tag("outcome", "success").timer().count());
                            assertTrue(registry.get("gateway.shortlist.backend").tag("outcome", "success").timer()
                                    .totalTime(TimeUnit.MILLISECONDS) >= 80,
                                    "Backend timing must include the blocked backend response");
                        }
                    });
        } finally {
            release.countDown();
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

}
