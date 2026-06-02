package com.example.job_portal_resume_service.service;


import java.util.List;

import com.example.dto.response.EducationResponse;
import com.example.job_portal_resume_service.payload.AddEducationRequest;

public interface EducationService {


    EducationResponse addEducation(
            Long resumeId,
            Long candidateId,
            AddEducationRequest request) throws Exception;

    List<EducationResponse> getEducations(Long resumeId);

    EducationResponse updateEducation(
            Long educationId,
            Long resumeId,
            Long candidateId,
            AddEducationRequest req) throws Exception;
    
    void deleteEducation(Long educationId, Long resumeId, Long candidateId) throws Exception;
}
