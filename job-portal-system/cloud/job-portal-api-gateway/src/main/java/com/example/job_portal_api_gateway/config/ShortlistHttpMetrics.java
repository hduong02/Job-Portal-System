package com.example.job_portal_api_gateway.config;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.EndpointInfo;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.util.TimeValue;

/** Timings contain no request IDs, URLs, or credentials. Backend timing ends at response headers. */
final class ShortlistHttpMetrics implements ExecChainHandler {
    private enum Phase { ACQUIRE, CONNECT, BACKEND }
    private final Map<Phase, Timer[]> timers = new EnumMap<>(Phase.class);
    private final LongTaskTimer acquiring;

    ShortlistHttpMetrics(MeterRegistry registry) {
        String[] names = {"gateway.shortlist.connection.acquire", "gateway.shortlist.connection.connect",
                "gateway.shortlist.backend"};
        for (Phase phase : Phase.values()) {
            timers.put(phase, new Timer[] {
                    Timer.builder(names[phase.ordinal()]).tag("outcome", "success")
                            .publishPercentileHistogram().register(registry),
                    Timer.builder(names[phase.ordinal()]).tag("outcome", "error")
                            .publishPercentileHistogram().register(registry)});
        }
        acquiring = LongTaskTimer.builder("gateway.shortlist.connection.acquiring").register(registry);
    }

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain)
            throws IOException, HttpException {
        if (!ShortlistAdmissionFilter.isStatusUpdate(scope.originalRequest.getMethod(),
                scope.originalRequest.getPath())) {
            return chain.proceed(request, scope);
        }
        var measured = new ExecChain.Scope(scope.exchangeId, scope.route, scope.originalRequest,
                new MeasuredRuntime(scope.execRuntime), scope.clientContext);
        return chain.proceed(request, measured);
    }

    private void record(Phase phase, long start, boolean success) {
        timers.get(phase)[success ? 0 : 1].record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    private final class MeasuredRuntime implements ExecRuntime {
        private final ExecRuntime delegate;
        private MeasuredRuntime(ExecRuntime delegate) { this.delegate = delegate; }

        @Override
        public void acquireEndpoint(String id, HttpRoute route, Object state, HttpClientContext context)
                throws IOException {
            long start = System.nanoTime();
            boolean success = false;
            var sample = acquiring.start();
            try {
                delegate.acquireEndpoint(id, route, state, context);
                success = true;
            } finally {
                record(Phase.ACQUIRE, start, success);
                sample.stop();
            }
        }

        @Override
        public void connectEndpoint(HttpClientContext context) throws IOException {
            long start = System.nanoTime();
            boolean success = false;
            try {
                delegate.connectEndpoint(context);
                success = true;
            } finally {
                record(Phase.CONNECT, start, success);
            }
        }

        @Override
        public ClassicHttpResponse execute(String id, ClassicHttpRequest request, HttpClientContext context)
                throws IOException, HttpException {
            return execute(id, request, null, context);
        }

        @Override
        public ClassicHttpResponse execute(String id, ClassicHttpRequest request,
                HttpResponseInformationCallback callback, HttpClientContext context)
                throws IOException, HttpException {
            long start = System.nanoTime();
            boolean success = false;
            try {
                var response = delegate.execute(id, request, callback, context);
                success = true;
                return response;
            } finally {
                record(Phase.BACKEND, start, success);
            }
        }

        @Override public boolean isExecutionAborted() { return delegate.isExecutionAborted(); }
        @Override public boolean isEndpointAcquired() { return delegate.isEndpointAcquired(); }
        @Override public void releaseEndpoint() { delegate.releaseEndpoint(); }
        @Override public void discardEndpoint() { delegate.discardEndpoint(); }
        @Override public boolean isEndpointConnected() { return delegate.isEndpointConnected(); }
        @Override public void disconnectEndpoint() throws IOException { delegate.disconnectEndpoint(); }
        @Override public void upgradeTls(HttpClientContext context) throws IOException { delegate.upgradeTls(context); }
        @Override public EndpointInfo getEndpointInfo() { return delegate.getEndpointInfo(); }
        @Override public boolean isConnectionReusable() { return delegate.isConnectionReusable(); }
        @Override public void markConnectionReusable(Object state, TimeValue duration) {
            delegate.markConnectionReusable(state, duration);
        }
        @Override public void markConnectionNonReusable() { delegate.markConnectionNonReusable(); }
        @Override public ExecRuntime fork(CancellableDependency dependency) {
            return new MeasuredRuntime(delegate.fork(dependency));
        }
    }
}
