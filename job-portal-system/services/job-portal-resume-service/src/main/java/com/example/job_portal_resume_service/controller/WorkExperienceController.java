package com.example.job_portal_resume_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.WorkExperienceResponse;
import com.example.job_portal_resume_service.payload.AddWorkExperienceRequest;
import com.example.job_portal_resume_service.service.WorkExperienceService;

import java.util.List;

@RestController
@RequestMapping("/api/resumes/{resumeId}/work-experiences")
@RequiredArgsConstructor
public class WorkExperienceController {

    private final WorkExperienceService workExperienceService;

    @PostMapping
    public ResponseEntity<WorkExperienceResponse> addWorkExperience(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddWorkExperienceRequest req) throws Exception {
        return ResponseEntity.ok(workExperienceService.addWorkExperience(
                resumeId,candidateId,req));
    }

    @GetMapping
    public ResponseEntity<List<WorkExperienceResponse>> getWorkExperiences(
            @PathVariable Long resumeId) throws Exception {
        return ResponseEntity.ok(workExperienceService.getWorkExperiences(resumeId));
    }

    @PutMapping("/{experienceId}")
    public ResponseEntity<WorkExperienceResponse> updateWorkExperience(
            @PathVariable Long resumeId,
            @PathVariable Long experienceId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddWorkExperienceRequest req) throws Exception {
        return ResponseEntity.ok(workExperienceService.updateWorkExperience(
                resumeId, candidateId, experienceId, req));
    }

    @DeleteMapping("/{experienceId}")
    public ResponseEntity<ApiResponse> deleteWorkExperience(
            @PathVariable Long resumeId,
            @PathVariable Long experienceId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        workExperienceService.deleteWorkExperience(resumeId, experienceId, candidateId);
        return ResponseEntity.ok(new ApiResponse("Work experience deleted successfully", true));
    }

}
