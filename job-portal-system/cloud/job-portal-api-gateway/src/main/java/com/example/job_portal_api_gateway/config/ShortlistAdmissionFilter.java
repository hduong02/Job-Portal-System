package com.example.job_portal_api_gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject overload before status requests allocate downstream HTTP resources. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ShortlistAdmissionFilter extends OncePerRequestFilter {
    private static final Pattern STATUS_PATH = Pattern.compile("/api/applications/[0-9]+/status");
    private final Semaphore permits;
    private final Counter accepted;
    private final Counter rejected;
    private final Timer success;
    private final Timer failure;

    public ShortlistAdmissionFilter(@Value("${gateway.shortlist.max-in-flight:32}") int maxInFlight,
            MeterRegistry registry) {
        if (maxInFlight < 1) throw new IllegalArgumentException("max-in-flight must be positive");
        permits = new Semaphore(maxInFlight);
        Gauge.builder("gateway.shortlist.in.flight", permits, value -> maxInFlight - value.availablePermits())
                .register(registry);
        accepted = registry.counter("gateway.shortlist.admission", "outcome", "accepted");
        rejected = registry.counter("gateway.shortlist.admission", "outcome", "rejected");
        success = Timer.builder("gateway.shortlist.request").tag("outcome", "success")
                .publishPercentileHistogram().register(registry);
        failure = Timer.builder("gateway.shortlist.request").tag("outcome", "error")
                .publishPercentileHistogram().register(registry);
    }

    static boolean isStatusUpdate(String method, String path) {
        return "PATCH".equals(method) && path != null && STATUS_PATH.matcher(path).matches();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isStatusUpdate(request.getMethod(), request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            rejected.increment();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "1");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many status updates in flight; try again shortly\"}");
            return;
        }
        accepted.increment();
        long start = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            permits.release();
            (completed && response.getStatus() < 400 ? success : failure)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
