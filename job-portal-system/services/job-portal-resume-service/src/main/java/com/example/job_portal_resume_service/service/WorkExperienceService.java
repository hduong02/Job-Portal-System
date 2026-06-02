package com.example.job_portal_resume_service.service;

import java.util.List;

import com.example.dto.response.WorkExperienceResponse;
import com.example.job_portal_resume_service.model.WorkExperience;
import com.example.job_portal_resume_service.payload.AddWorkExperienceRequest;

public interface WorkExperienceService {

    WorkExperienceResponse addWorkExperience(
            Long resumeId,
            Long candidateId,
            AddWorkExperienceRequest req) throws Exception;

    List<WorkExperienceResponse> getWorkExperiences(Long resumeId);

    WorkExperienceResponse updateWorkExperience(
            Long resumeId,
            Long candidateId,
            Long workExperienceId,
            AddWorkExperienceRequest req) throws Exception;

    void deleteWorkExperience(
            Long resumeId,
            Long workExperienceId,
            Long candidateId) throws Exception;

    WorkExperience getWorkExperienceEntity(Long workExperienceId) throws Exception;

}
