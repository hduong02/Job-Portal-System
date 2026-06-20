package com.example.job_portal_ai_service.config;

import com.google.genai.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientConfig {

    @Bean
    public Client genAiClient(GeminiProperties props){
        return Client.builder()
                .apiKey(props.getKey())
                .build();
    }
}
