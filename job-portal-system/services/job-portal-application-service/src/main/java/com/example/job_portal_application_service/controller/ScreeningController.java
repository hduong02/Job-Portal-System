package com.example.job_portal_application_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.job_portal_application_service.model.ApplicationScreening;
import com.example.dto.response.ApiResponse;
import com.example.job_portal_application_service.service.ApplicationScreeningService;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/application-screenings")
public class ScreeningController {

    private final ApplicationScreeningService applicationScreeningService;

    @PostMapping
    public ResponseEntity<ApiResponse> createScreenings(
            @RequestParam Long applicationId,
            @RequestParam Long candidateId,
            @RequestParam Long jobId,
            @RequestParam Long resumeId) {
        applicationScreeningService.screenAsync(applicationId, candidateId, jobId, resumeId);
        return ResponseEntity.accepted().body(new ApiResponse("Screening queued", true));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationScreening>> getScreenings() {
        return ResponseEntity.ok(applicationScreeningService.getAll());
    }
}
