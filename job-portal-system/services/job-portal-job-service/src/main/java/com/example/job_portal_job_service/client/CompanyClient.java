package com.example.job_portal_job_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.dto.response.CompanyResponse;

@FeignClient(name = "JOB-PORTAL-COMPANY-SERVICE")
public interface CompanyClient {

    @GetMapping("/api/companies/{id}")
    CompanyResponse getCompanyById(@PathVariable Long id);

    @GetMapping("/api/companies/my")
    CompanyResponse getMyCompany(@RequestHeader("X-User-Id") Long ownerId);

}

