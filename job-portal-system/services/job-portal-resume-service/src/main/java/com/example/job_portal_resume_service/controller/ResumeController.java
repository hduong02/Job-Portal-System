package com.example.job_portal_resume_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.PersonalInfoResponse;
import com.example.dto.response.ResumeResponse;
import com.example.job_portal_resume_service.payload.CreateResumeRequest;
import com.example.job_portal_resume_service.service.ResumeService;


@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping
    public ResponseEntity<ResumeResponse> createResume(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid CreateResumeRequest req
            ){
        return ResponseEntity.ok(resumeService.createResume(candidateId, req));
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<ResumeResponse> getResumeById(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        return ResponseEntity.ok(resumeService.getResumeById(resumeId, candidateId));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<ResumeResponse>> getMyResumes(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(resumeService.getMyResumes(candidateId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy))));
    }

    @PutMapping("/{resumeId}/personal-info")
    public ResponseEntity<ResumeResponse> updatePersonalInfo(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid PersonalInfoResponse req) throws Exception {
        return ResponseEntity.ok(resumeService.updatePersonalInfo(resumeId, candidateId, req));
    }

    @PatchMapping("/{resumeId}/summary")
    public ResponseEntity<ResumeResponse> updateSummary(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestParam String summary) throws Exception {
        return ResponseEntity.ok(resumeService.updateSummary(resumeId, candidateId, summary));
    }

    @PatchMapping("/{resumeId}/set-default")
    public ResponseEntity<ResumeResponse> setDefaultResume(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        return ResponseEntity.ok(resumeService.setDefaultResume(resumeId, candidateId));
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<ApiResponse> deleteResume(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        resumeService.deleteResume(resumeId, candidateId);
        return ResponseEntity.ok(new ApiResponse("Resume deleted successfully", true));
    }

}
