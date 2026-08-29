package com.example.job_portal_job_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.example.dto.request.JobRequest;
import com.example.dto.response.ApiResponse;
import com.example.dto.response.JobResponse;
import com.example.job_portal_job_service.payload.JobSearchRequest;
import com.example.job_portal_job_service.service.JobService;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @RequestHeader ("X-User-Id") Long employerId,
            @RequestBody @Valid JobRequest jobRequest) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.createJob(employerId, jobRequest));
    }

    @GetMapping("/{id}")
    @Cacheable(value = "jobs", key = "#id")
    public JobResponse getJobById(
            @PathVariable Long id) throws Exception {
        return jobService.getJobById(id);
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(
            @ModelAttribute JobSearchRequest req,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(jobService.getJobs(req, pageable(page, size, sortBy, sortDirection)));
    }

    @GetMapping("/company/{companyId}")
    public ResponseEntity<Page<JobResponse>> getJobsByCompany(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(jobService.getJobsByCompany(companyId, pageable(page, size, sortBy, sortDirection)));
    }

    @GetMapping("/admin")
    public ResponseEntity<Page<JobResponse>> getAllJobsAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(jobService.getAllJobsAdmin(pageable(page, size, sortBy, sortDirection)));
    }

    @PutMapping("/{id}")
    @Caching(evict = @CacheEvict(value = "jobs", key = "#id"))
    public ResponseEntity<JobResponse> updateJob(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employerId,
            @RequestBody @Valid JobRequest req)
            throws Exception {
        return ResponseEntity.ok(jobService.updateJob(id, employerId, req));
    }

    @PatchMapping("/{id}/publish")
    @Caching(evict = @CacheEvict(value = "jobs", key = "#id"))
    public ResponseEntity<JobResponse> publishJob(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employerId)
            throws Exception {
        return ResponseEntity.ok(jobService.publishJob(id, employerId));
    }

    @PatchMapping("/{id}/close")
    @Caching(evict = @CacheEvict(value = "jobs", key = "#id"))
    public ResponseEntity<JobResponse> closeJob(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employerId)
            throws Exception {
        return ResponseEntity.ok(jobService.closeJob(id, employerId));
    }

    @DeleteMapping("/{id}")
    @Caching(evict = @CacheEvict(value = "jobs", key = "#id"))
    public ResponseEntity<ApiResponse> deleteJob(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employerId)
            throws Exception {
        jobService.deleteJob(id, employerId);
        return ResponseEntity.ok(new ApiResponse("Job deleted successfully", true));
    }

    private PageRequest pageable(int page, int size, String sortBy, String sortDirection) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }
}
