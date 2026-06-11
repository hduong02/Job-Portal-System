package com.example.job_portal_application_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.dto.response.ResumeResponse;

@FeignClient(name = "JOB-PORTAL-RESUME-SERVICE")
public interface ResumeClient {
    @GetMapping("/api/resumes/{resumeId}")
    ResumeResponse getResumeById(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId
    );
}

