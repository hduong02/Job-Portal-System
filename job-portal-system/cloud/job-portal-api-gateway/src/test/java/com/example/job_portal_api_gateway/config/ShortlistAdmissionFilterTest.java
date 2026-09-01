package com.example.job_portal_api_gateway.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class ShortlistAdmissionFilterTest {
    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("PATCH", path);
        request.setServletPath(path); return request;
    }
    @Test
    void rejectsOverloadAndReturnsPermitAfterCompletion() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ShortlistAdmissionFilter(1, registry);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> {
                filter.doFilter(request("/api/applications/1/status"), new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new ServletException("timeout"); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new ServletException(error); }
                }); return null;
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(1, registry.get("gateway.shortlist.in.flight").gauge().value());
                var response = new MockHttpServletResponse();
                filter.doFilter(request("/api/applications/2/status"), response, (req, res) -> fail("must reject"));
                assertEquals(503, response.getStatus()); assertEquals("1", response.getHeader("Retry-After"));
                filter.doFilter(request("/api/applications/2/star"), new MockHttpServletResponse(), (req, res) -> {});
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
        }
        var recovered = new MockHttpServletResponse();
        filter.doFilter(request("/api/applications/3/status"), recovered, (req, res) -> {});
        assertEquals(200, recovered.getStatus());
        assertEquals(0, registry.get("gateway.shortlist.in.flight").gauge().value());
        assertEquals(2, registry.get("gateway.shortlist.admission").tag("outcome", "accepted").counter().count());
        assertEquals(1, registry.get("gateway.shortlist.admission").tag("outcome", "rejected").counter().count());
        assertEquals(2, registry.get("gateway.shortlist.request").tag("outcome", "success").timer().count());
    }
    @Test
    void exceptionDoesNotLeakPermit() throws Exception {
        var registry = new SimpleMeterRegistry();
        var filter = new ShortlistAdmissionFilter(1, registry);
        assertThrows(ServletException.class, () -> filter.doFilter(request("/api/applications/1/status"),
                new MockHttpServletResponse(), (req, res) -> { throw new ServletException("failure"); }));
        var response = new MockHttpServletResponse();
        filter.doFilter(request("/api/applications/2/status"), response, (req, res) -> {});
        assertEquals(200, response.getStatus());
        assertEquals(0, registry.get("gateway.shortlist.in.flight").gauge().value());
        assertEquals(1, registry.get("gateway.shortlist.request").tag("outcome", "error").timer().count());
    }
}
