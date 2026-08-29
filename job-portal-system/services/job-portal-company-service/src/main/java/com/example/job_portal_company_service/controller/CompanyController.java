package com.example.job_portal_company_service.controller;

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

import com.example.domain.CompanyStatus;
import com.example.domain.CompanyType;
import com.example.domain.IndustryType;
import com.example.dto.request.CompanyRequest;
import com.example.dto.response.ApiResponse;
import com.example.dto.response.CompanyResponse;
import com.example.job_portal_company_service.service.CompanyService;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(
            @RequestHeader ("X-User-Id") Long ownerId,
            @RequestBody @Valid CompanyRequest companyRequest
    ) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.createCompany(ownerId,companyRequest));
    }

    @GetMapping("/{id}")
    @Cacheable(value = "companies", key = "#id")
    public CompanyResponse getCompanyById(
            @PathVariable Long id
    ) throws Exception {
        return companyService.getCompanyById(id);
    }

    @GetMapping("/my")
    public ResponseEntity<CompanyResponse> getMyCompany(
            @RequestHeader("X-User-Id") Long ownerId) throws Exception {
        return ResponseEntity.ok(companyService.getMyCompany(ownerId));
    }

    @GetMapping
    public ResponseEntity<Page<CompanyResponse>> getAllCompanies(
            @RequestParam(required = false) CompanyType companyType,
            @RequestParam(required = false) IndustryType industryType,
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(
                companyService.getAllCompanies(companyType, industryType, status,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy))));
    }

    @PutMapping("/{id}")
    @Caching(evict = @CacheEvict(value = "companies", key = "#id"))
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long ownerId,
            @RequestBody @Valid CompanyRequest req)
            throws Exception {
        return ResponseEntity.ok(companyService.updateCompany(id, ownerId, req));
    }

    @PatchMapping("/{id}/verify")
    @Caching(evict = @CacheEvict(value = "companies", key = "#id"))
    public ResponseEntity<CompanyResponse> verifyCompany(
            @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(companyService.verifyCompany(id));
    }

    @PatchMapping("/{id}/deactivate")
    @Caching(evict = @CacheEvict(value = "companies", key = "#id"))
    public ResponseEntity<CompanyResponse> deactivateCompany(
            @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(companyService.deactivateCompany(id));
    }

    @DeleteMapping("/{id}")
    @Caching(evict = @CacheEvict(value = "companies", key = "#id"))
    public ResponseEntity<ApiResponse> deleteCompany(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long ownerId)
            throws Exception {
        companyService.deleteCompany(id, ownerId);
        return ResponseEntity.ok(new ApiResponse("Company deleted successfully", true));
    }


}
