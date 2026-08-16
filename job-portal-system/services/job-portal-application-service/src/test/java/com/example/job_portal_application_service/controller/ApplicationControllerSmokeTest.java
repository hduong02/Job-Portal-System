package com.example.job_portal_application_service.controller;

import com.example.domain.ApplicationStatus;
import com.example.dto.response.ApplicationResponse;
import com.example.dto.response.CompanyResponse;
import com.example.dto.response.JobResponse;
import com.example.dto.response.UserResponse;
import com.example.job_portal_application_service.payload.CreateApplicationRequest;
import com.example.job_portal_application_service.service.ApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicationService applicationService;

    @Test
    @DisplayName("Smoke Test: Job seeker successfully applies for a job via POST /api/applications")
    void shouldSuccessfullyCreateApplication() throws Exception {
        Long candidateId = 42L;
        Long jobId = 101L;
        Long resumeId = 202L;

        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .jobId(jobId)
                .resumeId(resumeId)
                .coverLetter("I am an experienced Software Engineer interested in this opportunity.")
                .expectedSalary(new BigDecimal("95000.00"))
                .availableFrom(LocalDate.of(2026, 10, 1))
                .build();

        ApplicationResponse mockResponse = ApplicationResponse.builder()
                .id(1L)
                .candidate(UserResponse.builder().id(candidateId).fullName("Alice Seeker").email("alice@example.com").build())
                .job(JobResponse.builder().id(jobId).title("Senior Backend Engineer").build())
                .company(CompanyResponse.builder().id(5L).name("TechCorp").build())
                .resumeId(resumeId)
                .coverLetter(request.getCoverLetter())
                .expectedSalary(request.getExpectedSalary())
                .availableFrom(request.getAvailableFrom())
                .status(ApplicationStatus.PENDING)
                .appliedAt(LocalDateTime.now())
                .build();

        when(applicationService.createApplication(eq(candidateId), any(CreateApplicationRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/applications")
                        .header("X-User-Id", candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.candidate.id").value(candidateId))
                .andExpect(jsonPath("$.job.id").value(jobId))
                .andExpect(jsonPath("$.resumeId").value(resumeId))
                .andExpect(jsonPath("$.coverLetter").value("I am an experienced Software Engineer interested in this opportunity."))
                .andExpect(jsonPath("$.expectedSalary").value(95000.00));

        verify(applicationService).createApplication(eq(candidateId), any(CreateApplicationRequest.class));
    }

    @Test
    @DisplayName("Smoke Test: Missing X-User-Id header returns 400 Bad Request")
    void shouldReturnBadRequestWhenMissingUserIdHeader() throws Exception {
        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .jobId(101L)
                .resumeId(202L)
                .build();

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Smoke Test: Missing required fields (jobId & resumeId) returns 400 Bad Request")
    void shouldReturnBadRequestWhenMissingRequiredBodyFields() throws Exception {
        CreateApplicationRequest invalidRequest = CreateApplicationRequest.builder()
                .coverLetter("Missing jobId and resumeId")
                .build();

        mockMvc.perform(post("/api/applications")
                        .header("X-User-Id", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
