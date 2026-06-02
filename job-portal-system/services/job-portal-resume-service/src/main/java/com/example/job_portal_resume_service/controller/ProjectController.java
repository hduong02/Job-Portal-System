package com.example.job_portal_resume_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.ProjectResponse;
import com.example.job_portal_resume_service.payload.AddProjectRequest;
import com.example.job_portal_resume_service.service.ProjectService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resumes/{resumeId}/projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> addProject(
            @PathVariable("resumeId") Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddProjectRequest request) throws Exception {
        return ResponseEntity.ok(
                projectService.addProject(resumeId, candidateId, request)
        );
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(
            @PathVariable Long resumeId) {
        return ResponseEntity.ok(projectService.getAllProjects(resumeId));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long resumeId,
            @PathVariable Long projectId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddProjectRequest req) throws Exception {
        return ResponseEntity.ok(
                projectService.updateProject(projectId, resumeId, candidateId, req));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<ApiResponse> deleteProject(
            @PathVariable Long resumeId,
            @PathVariable Long projectId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        projectService.deleteProject(projectId, resumeId, candidateId);
        return ResponseEntity.ok(new ApiResponse("Project deleted successfully", true));
    }
}
