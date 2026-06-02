package com.example.job_portal_resume_service.service;

import java.util.List;

import com.example.dto.response.ResumeSkillResponse;
import com.example.job_portal_resume_service.payload.AddResumeSkillRequest;

public interface ResumeSkillService {

    ResumeSkillResponse addSkill(
            Long resumeId,
            Long candidateId,
            AddResumeSkillRequest req) throws Exception;

    List<ResumeSkillResponse> getSkills(Long resumeId);

    ResumeSkillResponse updateSkill(
            Long skillId,
            Long resumeId,
            Long candidateId,
            AddResumeSkillRequest req) throws Exception;
            
    void deleteSkill(Long skillId, Long resumeId, Long candidateId) throws Exception;
}
