package com.example.job_portal_resume_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.dto.response.PersonalInfoResponse;
import com.example.dto.response.ResumeResponse;
import com.example.job_portal_resume_service.model.Resume;
import com.example.job_portal_resume_service.payload.CreateResumeRequest;

public interface ResumeService {

    ResumeResponse createResume(Long candidateId, CreateResumeRequest req);

    ResumeResponse getResumeById(Long resumeId, Long candidateId) throws Exception;

    Page<ResumeResponse> getMyResumes(Long candidateId, Pageable pageable);

    ResumeResponse updatePersonalInfo(
            Long resumeId,
            Long candidateId,
            PersonalInfoResponse req) throws Exception;

    ResumeResponse updateSummary(
            Long resumeId,
            Long candidateId,
            String summary) throws Exception;

    ResumeResponse setDefaultResume(Long resumeId, Long candidateId) throws Exception;

    void deleteResume(Long resumeId, Long candidateId) throws Exception;

    Resume getResumeEntity(Long resumeId) throws Exception;

}
