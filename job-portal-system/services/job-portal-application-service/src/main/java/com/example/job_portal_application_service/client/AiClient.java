package com.example.job_portal_application_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.job_portal_application_service.payload.ScreeningScoreRequest;
import com.example.job_portal_application_service.payload.ScreeningScoreResponse;

@FeignClient("job-portal-ai-service")
public interface AiClient {

    @PostMapping("/api/ai/application/screening-score")
    ScreeningScoreResponse scoreCandidate(
            @RequestBody ScreeningScoreRequest request);
}
