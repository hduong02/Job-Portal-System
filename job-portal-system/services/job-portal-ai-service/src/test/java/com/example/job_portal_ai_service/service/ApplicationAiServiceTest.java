package com.example.job_portal_ai_service.service;

import com.example.job_portal_ai_service.client.GeminiClient;
import com.example.job_portal_ai_service.payload.ScreeningScoreRequest;
import com.example.job_portal_ai_service.payload.ScreeningScoreResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationAiServiceTest {

    private final GeminiClient gemini = mock(GeminiClient.class);
    private final ApplicationAiService service = new ApplicationAiService(gemini);

    @Test
    void screeningUsesCandidateSkillsSeparatelyFromJobRequirements() throws Exception {
        ScreeningScoreRequest request = new ScreeningScoreRequest();
        request.setRequiredSkills(List.of("Java", "Spring Boot"));
        request.setCandidateSkills(List.of("Python", "Django"));

        String prompt = screeningPrompt(request);

        assertTrue(prompt.contains("- Required Skills: Java, Spring Boot"));
        assertTrue(prompt.contains("- Skills: Python, Django"));
        assertFalse(prompt.contains("- Skills: Java, Spring Boot"));
    }

    @Test
    void missingCandidateSkillsDoNotInheritJobRequirements() throws Exception {
        ScreeningScoreRequest request = new ScreeningScoreRequest();
        request.setRequiredSkills(List.of("Java"));

        String prompt = screeningPrompt(request);

        assertTrue(prompt.contains("- Required Skills: Java"));
        assertTrue(prompt.contains("- Skills: Not Provided"));
        assertFalse(prompt.contains("- Skills: Java"));
    }

    @Test
    void candidateSkillsAreIncludedWhenJobRequirementsAreMissing() throws Exception {
        ScreeningScoreRequest request = new ScreeningScoreRequest();
        request.setCandidateSkills(List.of("Python"));

        String prompt = screeningPrompt(request);

        assertTrue(prompt.contains("- Required Skills: Not Provided"));
        assertTrue(prompt.contains("- Skills: Python"));
    }

    @Test
    void screeningIncludesEducationReceivedInRequestJson() throws Exception {
        ScreeningScoreRequest request = JsonMapper.builder().build().readValue("""
                {
                  "candidateEducation": [
                    "Bachelor in Computer Science from MIT (3.6)",
                    "Master in Software Engineering from Stanford"
                  ]
                }
                """, ScreeningScoreRequest.class);

        String prompt = screeningPrompt(request);

        assertTrue(prompt.contains("- Education: Bachelor in Computer Science from MIT (3.6), "
                + "Master in Software Engineering from Stanford"));
    }

    @Test
    void missingEducationIsExplicitInScreeningPrompt() throws Exception {
        String prompt = screeningPrompt(new ScreeningScoreRequest());

        assertTrue(prompt.contains("- Education: Not Provided"));
    }

    @Test
    void emptyEducationIsExplicitInScreeningPrompt() throws Exception {
        ScreeningScoreRequest request = JsonMapper.builder().build().readValue(
                "{\"candidateEducation\": []}", ScreeningScoreRequest.class);

        String prompt = screeningPrompt(request);

        assertTrue(prompt.contains("- Education: Not Provided"));
    }

    private String screeningPrompt(ScreeningScoreRequest request) throws Exception {
        ScreeningScoreResponse response = new ScreeningScoreResponse();
        when(gemini.generateJson(anyString(), anyString(), eq(ScreeningScoreResponse.class)))
                .thenReturn(response);

        assertSame(response, service.scoreCandidate(request));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateJson(anyString(), prompt.capture(), eq(ScreeningScoreResponse.class));
        return prompt.getValue();
    }
}
