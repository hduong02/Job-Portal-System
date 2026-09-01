package com.example.job_portal_api_gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GatewayHttpClientConfig {
    @Bean
    ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> gatewayHttpPoolCustomizer(
            @Value("${gateway.shortlist.max-in-flight:32}") int maxInFlight,
            @Value("${gateway.shortlist.connection-request-timeout-ms:200}") long acquisitionTimeoutMs,
            MeterRegistry registry) {
        if (maxInFlight < 1) throw new IllegalArgumentException("max-in-flight must be positive");
        if (acquisitionTimeoutMs < 1) {
            throw new IllegalArgumentException("connection-request-timeout-ms must be positive");
        }
        var metrics = new ShortlistHttpMetrics(registry);
        // Preserve Boot's TLS, proxy, connection and read settings while bounding pool acquisition.
        return builder -> builder.withConnectionManagerCustomizer(manager -> manager
                .setMaxConnTotal(Math.max(512, maxInFlight))
                .setMaxConnPerRoute(maxInFlight))
                .withHttpClientCustomizer(client -> client.addExecInterceptorBefore(
                        ChainElement.CONNECT.name(), "shortlist-timings", (request, scope, chain) -> {
                            if (ShortlistAdmissionFilter.isStatusUpdate(scope.originalRequest.getMethod(),
                                    scope.originalRequest.getPath())) {
                                scope.clientContext.setRequestConfig(RequestConfig
                                        .copy(scope.clientContext.getRequestConfig())
                                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(acquisitionTimeoutMs))
                                        .build());
                            }
                            return metrics.execute(request, scope, chain);
                        }));
    }
}
