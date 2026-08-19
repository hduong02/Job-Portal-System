package com.example.job_portal_job_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.dto.request.JobRequest;
import com.example.dto.response.JobResponse;
import com.example.job_portal_job_service.payload.JobSearchRequest;

public interface JobService {

    JobResponse createJob(Long employerId, JobRequest req) throws Exception;

    JobResponse getJobById(Long id) throws Exception;

    Page<JobResponse> getJobs(JobSearchRequest request, Pageable pageable);

    Page<JobResponse> getJobsByCompany(Long companyId, Pageable pageable);

    JobResponse updateJob(Long jobId, Long employerId, JobRequest req) throws Exception;

    JobResponse publishJob(Long jobId, Long employerId) throws Exception;

    JobResponse closeJob(Long jobId, Long employerId) throws Exception;

    void deleteJob(Long jobId, Long employerId) throws Exception;

    Page<JobResponse> getAllJobsAdmin(Pageable pageable);
}
