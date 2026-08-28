package com.example.job_portal_api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalConfigurationTest {

    @Test
    void localSettingsResolveWithoutConfigServer() {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("job-portal-config"))) {
            root = root.getParent();
            if (root == null) {
                throw new IllegalStateException("Cannot locate repository root");
            }
        }

        Map<String, Integer> ports = Map.of(
                "user", 5001, "company", 5002, "job", 5003, "resume", 5004,
                "application", 5005, "preference", 5006, "ai", 6000,
                "notification", 5011, "api-gateway", 5000);
        for (var entry : ports.entrySet()) {
            String service = entry.getKey();
            String module = service.equals("api-gateway") ? "cloud" : "services";
            String name = "job-portal-" + service + (service.equals("api-gateway") ? "" : "-service");
            Path yaml = root.resolve("job-portal-system/" + module + "/" + name
                    + "/src/main/resources/application.yaml");
            SpringApplication application = new SpringApplication(ConfigurationOnly.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(
                    "--spring.config.location=" + yaml.toUri(),
                    "--spring.cloud.config.enabled=false",
                    "--DB_PASSWORD=local-config-test-password",
                    "--GEMINI_API_KEY=local-config-test-key",
                    "--spring.main.banner-mode=off")) {
                var environment = context.getEnvironment();
                assertEquals(entry.getValue(), environment.getProperty("server.port", Integer.class), name);
                assertEquals("http://localhost:8761/eureka/",
                        environment.getProperty("eureka.client.service-url.defaultZone"), name);
                if (!service.equals("api-gateway") && !service.equals("ai") && !service.equals("notification")) {
                    assertEquals("jdbc:postgresql://localhost:5432/job_portal_" + service,
                            environment.getProperty("spring.datasource.url"), name);
                    assertEquals("postgres", environment.getProperty("spring.datasource.username"), name);
                    assertEquals("local-config-test-password",
                            environment.getProperty("spring.datasource.password"), name);
                    assertEquals("update", environment.getProperty("spring.jpa.hibernate.ddl-auto"), name);
                }
                if (service.equals("ai")) {
                    assertEquals("local-config-test-key", environment.getProperty("gemini.api.key"), name);
                }
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfigurationOnly {
    }
}
