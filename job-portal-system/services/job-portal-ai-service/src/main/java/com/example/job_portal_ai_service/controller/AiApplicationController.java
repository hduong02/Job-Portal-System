package com.example.job_portal_ai_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.job_portal_ai_service.payload.AiTextResponse;
import com.example.job_portal_ai_service.payload.CoverLetterRequest;
import com.example.job_portal_ai_service.payload.ScreeningScoreRequest;
import com.example.job_portal_ai_service.payload.ScreeningScoreResponse;
import com.example.job_portal_ai_service.payload.SkillsGapRequest;
import com.example.job_portal_ai_service.payload.SkillsGapResponse;
import com.example.job_portal_ai_service.service.ApplicationAiService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/application")
public class AiApplicationController {

    private final ApplicationAiService applicationAiService;

    @PostMapping("/cover-letter")
    public ResponseEntity<AiTextResponse> generateCoverLetter(
            @Valid @RequestBody CoverLetterRequest request) throws Exception {
        AiTextResponse response = applicationAiService.generateCoverLetter(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/screening-score")
    public ResponseEntity<ScreeningScoreResponse> scoreCandidate(
            @Valid @RequestBody ScreeningScoreRequest request) throws Exception {
        return ResponseEntity.ok(applicationAiService.scoreCandidate(request));
    }

    @PostMapping("/skills-gap")
    public ResponseEntity<SkillsGapResponse> analyzeSkillsGap(
            @Valid @RequestBody SkillsGapRequest request) throws Exception {
        return ResponseEntity.ok(applicationAiService.analyzeSkillsGap(request));
    }
}
