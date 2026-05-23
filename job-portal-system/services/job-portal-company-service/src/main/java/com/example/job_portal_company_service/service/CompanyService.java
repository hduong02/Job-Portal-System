package com.example.job_portal_company_service.service;

import java.util.List;

import com.example.domain.CompanyStatus;
import com.example.domain.CompanyType;
import com.example.domain.IndustryType;
import com.example.dto.request.CompanyRequest;
import com.example.dto.response.CompanyResponse;
import com.example.job_portal_company_service.model.Company;

public interface CompanyService {

    CompanyResponse createCompany(Long ownerId, CompanyRequest req) throws Exception;
    CompanyResponse getCompanyById(Long id) throws Exception;
    CompanyResponse getMyCompany(Long ownerId) throws Exception;
    List<CompanyResponse> getAllCompanies(
            CompanyType companyType,
            IndustryType industryType,
            CompanyStatus companyStatus
    );
    CompanyResponse updateCompany(Long companyId, Long ownerId, CompanyRequest req)
            throws Exception;
    CompanyResponse verifyCompany(Long companyId) throws Exception;
    void deleteCompany(Long companyId, Long ownerId) throws Exception;
    CompanyResponse deactivateCompany(Long companyId) throws Exception;

    Company getCompanyEntityById(Long id) throws Exception;

}