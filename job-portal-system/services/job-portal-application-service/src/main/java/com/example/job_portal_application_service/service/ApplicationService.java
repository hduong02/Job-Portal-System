package com.example.job_portal_application_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.domain.ApplicationStatus;
import com.example.dto.response.ApplicationResponse;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.payload.CompanyApplicationFilterRequest;
import com.example.job_portal_application_service.payload.CreateApplicationRequest;
import com.example.job_portal_application_service.payload.WithdrawApplicationRequest;


public interface ApplicationService {

    ApplicationResponse createApplication(
            Long candidateId,
            CreateApplicationRequest req) throws Exception;

    ApplicationResponse getApplicationById(Long id, Long requesterId) throws Exception;

    Page<ApplicationResponse> getMyApplications(Long candidateId, Pageable pageable);

    Page<ApplicationResponse> getApplicationsForJob(Long jobId, Long employerId, Pageable pageable);

    Page<ApplicationResponse> getApplicationsForCompany(Long userId,
            CompanyApplicationFilterRequest filter, Pageable pageable);

    ApplicationResponse updateStatus(
            Long applicationId,
            Long employerId,
            ApplicationStatus status) throws Exception;

    ApplicationResponse withdraw(
            Long applicationId,
            Long candidateId,
            WithdrawApplicationRequest req) throws Exception;

    ApplicationResponse toggleStar(
            Long applicationId,
            Long employerId) throws Exception;

    void deleteApplication(Long applicationId, Long candidateId) throws Exception;

    Application getApplicationEntity(Long id) throws Exception;

}
