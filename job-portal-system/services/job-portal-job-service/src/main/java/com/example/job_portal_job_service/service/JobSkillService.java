package com.example.job_portal_job_service.service;

import java.util.List;
import java.util.Set;

import com.example.dto.response.JobSkillResponse;
import com.example.job_portal_job_service.model.JobSkill;
import com.example.job_portal_job_service.payload.JobSkillRequest;

public interface JobSkillService {

    JobSkillResponse createSkill(JobSkillRequest req) throws Exception;

    List<JobSkillResponse> getAllSkills();

    JobSkillResponse getSkillById(Long id) throws Exception;

    JobSkillResponse updateSkill(Long id, JobSkillRequest req) throws Exception;

    void deleteSkill(Long id) throws Exception;

    Set<JobSkill> getSkillsByIds(Set<Long> ids);
}

