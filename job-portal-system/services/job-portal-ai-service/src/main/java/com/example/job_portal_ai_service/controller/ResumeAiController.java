package com.example.job_portal_ai_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.job_portal_ai_service.payload.*;
import com.example.job_portal_ai_service.service.ResumeAiService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/resume")
public class ResumeAiController {

    private final ResumeAiService resumeAiService;

    @PostMapping("/summary")
    public ResponseEntity<AiTextResponse> generateSummary(
            @RequestBody ResumeSummaryRequest resumeSummaryRequest)
            throws Exception {
        AiTextResponse response = resumeAiService.generateProfessionalSummary(
                resumeSummaryRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/experience-bullets")
    public ResponseEntity<WorkExperienceBulletsResponse> generateBullets(
            @Valid @RequestBody WorkExperienceBulletRequest request) throws Exception {
        WorkExperienceBulletsResponse response = resumeAiService
                .generateWorkExperienceBullets(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/improvements")
    public ResponseEntity<ResumeImprovementResponse> getImprovements(
            @Valid @RequestBody ResumeImprovementRequest request) throws Exception {
        ResumeImprovementResponse response = resumeAiService.getResumeImprovementTips(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/career-feedback")
    public ResponseEntity<CareerFeedbackResponse> getImprovements(
            @Valid @RequestBody CareerFeedbackRequest request) throws Exception {
        CareerFeedbackResponse response = resumeAiService.getCareerFeedback(request);
        return ResponseEntity.ok(response);
    }
}

