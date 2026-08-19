package com.example.job_portal_application_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.ApplicationResponse;
import com.example.job_portal_application_service.payload.CompanyApplicationFilterRequest;
import com.example.job_portal_application_service.payload.CreateApplicationRequest;
import com.example.job_portal_application_service.payload.UpdateApplicationStatusRequest;
import com.example.job_portal_application_service.payload.WithdrawApplicationRequest;
import com.example.job_portal_application_service.service.ApplicationService;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApplicationResponse> createApplication(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid CreateApplicationRequest createApplicationRequest)
            throws Exception {
        return ResponseEntity.ok(
                applicationService.createApplication(candidateId, createApplicationRequest)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplicationById(
            @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(applicationService.getApplicationById(id));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<ApplicationResponse>> getMyApplications(
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(applicationService.getMyApplications(candidateId,
                pageable(page, size, applicationSort(sortBy), sortDirection)));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<Page<ApplicationResponse>> getApplicationsForJob(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(applicationService.getApplicationsForJob(jobId,
                pageable(page, size, applicationSort(sortBy), sortDirection)));
    }

    @GetMapping("/company")
    public ResponseEntity<Page<ApplicationResponse>> getApplicationsForCompany(
            @RequestHeader("X-User-Id") Long userId,
            @ModelAttribute CompanyApplicationFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) throws Exception {
        return ResponseEntity.ok(applicationService.getApplicationsForCompany(
                userId, filter, pageable(page, size, applicationSort(sortBy), sortDirection)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employerId,
            @RequestBody @Valid UpdateApplicationStatusRequest req)
            throws Exception {
        return ResponseEntity.ok(applicationService.updateStatus(id,
                employerId,
                req.getStatus())
        );
    }

    @PatchMapping("/{id}/withdraw")
    public ResponseEntity<ApplicationResponse> withdraw(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody WithdrawApplicationRequest req)
            throws Exception {
        return ResponseEntity.ok(applicationService.withdraw(id, candidateId, req));
    }

    @PatchMapping("/{id}/star")
    public ResponseEntity<ApplicationResponse> toggleStar(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long employerId)
            throws Exception {
        return ResponseEntity.ok(applicationService.toggleStar(id, employerId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteApplication(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long candidateId)
            throws Exception {
        applicationService.deleteApplication(id, candidateId);
        return ResponseEntity.ok(
                new ApiResponse("Application deleted successfully", true));
    }

    private PageRequest pageable(int page, int size, String sortBy, String sortDirection) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }

    private String applicationSort(String sortBy) {
        return "createdAt".equals(sortBy) ? "appliedAt" : sortBy;
    }
}
