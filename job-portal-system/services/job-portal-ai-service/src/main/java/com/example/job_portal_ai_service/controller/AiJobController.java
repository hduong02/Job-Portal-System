package com.example.job_portal_ai_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.job_portal_ai_service.payload.AiTextResponse;
import com.example.job_portal_ai_service.payload.JobDescriptionRequest;
import com.example.job_portal_ai_service.payload.SalaryRangeRequest;
import com.example.job_portal_ai_service.payload.SalaryRangeResponse;
import com.example.job_portal_ai_service.service.JobAiService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/job")
public class AiJobController {

    private final JobAiService jobAiService;

    @PostMapping("/describe")
    public ResponseEntity<AiTextResponse> generateJobDescription(
            @Valid @RequestBody JobDescriptionRequest request)
            throws Exception {
        AiTextResponse response = jobAiService.generateJobDescription(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requirements")
    public ResponseEntity<AiTextResponse> generateJobRequirements(
            @RequestParam String title,
            @RequestParam(required = false) String category)
            throws Exception {
        AiTextResponse response = jobAiService.generateJobRequirements(title, category);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/salary-suggestion")
    public ResponseEntity<SalaryRangeResponse> suggestSalary(
            @Valid @RequestBody SalaryRangeRequest request)
            throws Exception {
        SalaryRangeResponse response = jobAiService.suggestSalaryRange(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/skills-recommendation")
    public ResponseEntity<AiTextResponse> recommendSkills(
            @RequestParam String title,
            @RequestParam(required = false) String category)
            throws Exception {
        AiTextResponse response = jobAiService.recommendSkillForJob(title, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/responsibilities")
    public ResponseEntity<AiTextResponse> generateResponsibilities(
            @RequestParam String title,
            @RequestParam(required = false) String category)
            throws Exception {
        AiTextResponse response = jobAiService.generateJobResponsibilities(title, category);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/benefits")
    public ResponseEntity<AiTextResponse> generateBenefits(
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String jobType)
            throws Exception {
        AiTextResponse response = jobAiService.generateJobBenefits(title, category, jobType);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tags-recommendation")
    public ResponseEntity<AiTextResponse> recommendTags(
            @RequestParam String title,
            @RequestParam(required = false) String description)
            throws Exception {
        AiTextResponse response = jobAiService.recommendTagsForJob(title, description);
        return ResponseEntity.ok(response);
    }
}
