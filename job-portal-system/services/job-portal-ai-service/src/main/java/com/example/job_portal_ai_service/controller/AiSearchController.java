package com.example.job_portal_ai_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.job_portal_ai_service.payload.JobAlertSuggestRequest;
import com.example.job_portal_ai_service.payload.JobAlertSuggestResponse;
import com.example.job_portal_ai_service.payload.SearchEnhanceRequest;
import com.example.job_portal_ai_service.payload.SearchEnhanceResponse;
import com.example.job_portal_ai_service.service.SearchAiService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiSearchController {

    private final SearchAiService searchAiService;

    @PostMapping("/search/enhance")
    public ResponseEntity<SearchEnhanceResponse> enhanceSearch(
            @Valid @RequestBody SearchEnhanceRequest request) throws Exception {
        return ResponseEntity.ok(searchAiService.enhanceSearch(request));
    }

    @PostMapping("/alert-suggestion")
    public ResponseEntity<JobAlertSuggestResponse> suggestAlertCriteria(
            @Valid @RequestBody JobAlertSuggestRequest request) throws Exception {
        return ResponseEntity.ok(searchAiService.suggestJobAlertCriteria(request));
    }
}
