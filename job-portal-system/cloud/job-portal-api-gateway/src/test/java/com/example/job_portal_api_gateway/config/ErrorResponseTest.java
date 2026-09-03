package com.example.job_portal_api_gateway.config;

import com.example.job_portal_api_gateway.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.*;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ErrorServer {
        @Bean
        RouterFunction<ServerResponse> routes() {
            var auth = new RouteConfig(new JwtUtil());
            return RouterFunctions.route()
                    .GET("/protected", request -> ServerResponse.ok().build())
                    .before(auth::jwtAuthFilter)
                    .build()
                    .and(RouterFunctions.route()
                            .GET("/failure", request -> { throw new IllegalStateException("private backend details"); })
                            .GET("/validation", request -> { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "private validation details"); })
                            .build());
        }
    }

    @Test
    void errorsNeverExposeTracesEvenWhenRequestedByClient() throws Exception {
        var application = new SpringApplication(ErrorServer.class);
        // Reproduce the development defaults used when devtools is on the runtime classpath.
        application.setDefaultProperties(Map.of("spring.web.error.include-stacktrace", "always",
                "spring.web.error.include-message", "always", "spring.web.error.include-exception", "true"));
        try (var context = application.run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                "--spring.cloud.discovery.enabled=false", "--spring.cloud.compatibility-verifier.enabled=false");
             var client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            for (String path : new String[]{"/protected", "/validation", "/failure"}) {
                for (boolean malformedToken : new boolean[]{false, true}) {
                    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                            + path + "?trace=true&message=true&errors=true")).header("Accept", "application/json");
                    if (malformedToken) builder.header("Authorization", "Bearer malformed-token");
                    var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(path.equals("/protected") ? 401 : path.equals("/validation") ? 400 : 500,
                            response.statusCode());
                    assertFalse(response.body().contains("\"trace\""), response.body());
                    assertFalse(response.body().contains("\"exception\""), response.body());
                    assertFalse(response.body().contains("private"), response.body());
                    assertFalse(response.body().contains("com.example"), response.body());
                }
            }
        }
    }
}
