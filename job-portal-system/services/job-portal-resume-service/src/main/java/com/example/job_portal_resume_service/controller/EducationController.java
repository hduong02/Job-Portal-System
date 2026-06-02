package com.example.job_portal_resume_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.EducationResponse;
import com.example.job_portal_resume_service.payload.AddEducationRequest;
import com.example.job_portal_resume_service.service.EducationService;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/resumes/{resumeId}/educations")
public class EducationController {

    private final EducationService educationService;

    @PostMapping
    public ResponseEntity<EducationResponse> addEducation(
            @PathVariable("resumeId") Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddEducationRequest req
            ) throws Exception {
        return ResponseEntity.ok(
                educationService.addEducation(resumeId, candidateId, req)
        );
    }

    @GetMapping
    public ResponseEntity<List<EducationResponse>> getEducations(
            @PathVariable Long resumeId) throws Exception {
        return ResponseEntity.ok(educationService.getEducations(resumeId));
    }

    @PutMapping("/{educationId}")
    public ResponseEntity<EducationResponse> updateEducation(
            @PathVariable Long resumeId,
            @PathVariable Long educationId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddEducationRequest req) throws Exception {
        return ResponseEntity.ok(
                educationService.updateEducation(educationId, resumeId, candidateId, req));
    }

    @DeleteMapping("/{educationId}")
    public ResponseEntity<ApiResponse> deleteEducation(
            @PathVariable Long resumeId,
            @PathVariable Long educationId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        educationService.deleteEducation(educationId, resumeId, candidateId);
        return ResponseEntity.ok(new ApiResponse("Education deleted successfully", true));
    }
}