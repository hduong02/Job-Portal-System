package com.example.job_portal_ai_service.client;

import com.example.job_portal_ai_service.config.GeminiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final Client genaiClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;


    public String generateText(String systemInstruction, String prompt) throws Exception {
        return generateText(
                systemInstruction,
                prompt,
                geminiProperties.getTemperature(),
                geminiProperties.getMaxOutputTokens());
    }

    public <T> T generateJson(
            String systemInstruction,
            String prompt,
            Class<T> responseType) throws Exception {
        return callJson(systemInstruction, prompt, responseType);
    }

    public String generateText(
            String systemInstruction,
            String prompt,
            double temperature,
            int maxTokens) throws Exception {
        return callText(
                systemInstruction,
                prompt,
                (float) geminiProperties.getTemperature(),
                geminiProperties.getMaxOutputTokens());
    }

    private <T> T callJson(
            String systemInstruction,
            String prompt,
            Class<T> responseType) throws Exception {
        try {
            GenerateContentConfig config = buildConfig(
                systemInstruction,
                0.3f,
                geminiProperties.getMaxOutputTokens(),
                true
            );

            GenerateContentResponse response = genaiClient.models.generateContent(
                    geminiProperties.getModel(),
                    prompt,
                    config);
            String text = response.text();
            return objectMapper.readValue(text, responseType);

        } catch (Exception e) {
            throw new Exception("Failed to get response from gemini:" + e.getMessage());
        }
    }

    private String callText(
            String systemInstruction,
            String prompt,
            float temperature,
            int maxTokens) throws Exception {
        try {
            GenerateContentConfig config = buildConfig(
                systemInstruction,
                temperature,
                maxTokens,
                false);
            GenerateContentResponse response = genaiClient.models.generateContent(
                    geminiProperties.getModel(),
                    prompt,
                    config
            );
            String text = response.text();
            return text;

        } catch (Exception e) {
            throw new Exception("Failed to get response from gemini:" + e.getMessage());
        }
    }

    private GenerateContentConfig buildConfig(
            String systemInstruction,
            float temperature,
            int maxTokens,
            boolean jsonMode) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder()
                .temperature((float)temperature).maxOutputTokens(maxTokens);

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            builder.systemInstruction(
                    Content.fromParts(Part.fromText(systemInstruction))
            );
        }
        if (jsonMode) {
            builder.responseMimeType("application/json");
        }
        return builder.build();
    }
}