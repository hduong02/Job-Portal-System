package com.example.job_portal_preference_service.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.SavedJobResponse;
import com.example.job_portal_preference_service.payload.SaveJobRequest;
import com.example.job_portal_preference_service.service.SavedJobService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/preferences/saved-jobs")
public class SavedJobController {

    private final SavedJobService savedJobService;

    @PostMapping
    public ResponseEntity<SavedJobResponse> saveJob(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody SaveJobRequest req)
            throws Exception {
        return ResponseEntity.ok(savedJobService.saveJob(candidateId, req));
    }

    @GetMapping
    public ResponseEntity<Page<SavedJobResponse>> getMySavedJobs(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        String savedJobSort = "createdAt".equals(sortBy) ? "savedAt" : sortBy;
        return ResponseEntity.ok(savedJobService.getSavedJob(candidateId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), savedJobSort))));
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isSaved(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestParam Long jobId) {
        return ResponseEntity.ok(savedJobService.isSaved(candidateId, jobId));
    }

    @DeleteMapping("/{savedJobId}")
    public ResponseEntity<ApiResponse> unsaveJob(
            @PathVariable Long savedJobId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        savedJobService.unsaveJob(candidateId, savedJobId);
        return ResponseEntity.ok(new ApiResponse("Job removed from saved list", true));
    }
}
