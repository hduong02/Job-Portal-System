package com.example.job_portal_resume_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.ResumeSkillResponse;
import com.example.job_portal_resume_service.payload.AddResumeSkillRequest;
import com.example.job_portal_resume_service.service.ResumeSkillService;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/resumes/{resumeId}/skills")
public class ResumeSkillController {
    private final ResumeSkillService resumeSkillService;

    @PostMapping
    public ResponseEntity<ResumeSkillResponse> addSkill(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddResumeSkillRequest req) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeSkillService.addSkill(resumeId, candidateId, req));
    }

    @GetMapping
    public ResponseEntity<List<ResumeSkillResponse>> getSkills(
            @PathVariable Long resumeId) {
        return ResponseEntity.ok(resumeSkillService.getSkills(resumeId));
    }

    @PutMapping("/{skillId}")
    public ResponseEntity<ResumeSkillResponse> updateSkill(
            @PathVariable Long resumeId,
            @PathVariable Long skillId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddResumeSkillRequest req) throws Exception {
        return ResponseEntity.ok(
                resumeSkillService.updateSkill(skillId, resumeId, candidateId, req));
    }

    @DeleteMapping("/{skillId}")
    public ResponseEntity<ApiResponse> deleteSkill(
            @PathVariable Long resumeId,
            @PathVariable Long skillId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        resumeSkillService.deleteSkill(skillId, resumeId, candidateId);
        return ResponseEntity.ok(new ApiResponse("Skill deleted successfully", true));
    }
}
