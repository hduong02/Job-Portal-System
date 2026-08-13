package com.example.job_portal_api_gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173"
                        // "https://job-portal-frontend.vercel.app"
                )
                .allowCredentials(true)
                .allowedMethods("*")
                .exposedHeaders("Authorization")
                .maxAge(3600);
    }
}