package com.example.job_portal_resume_service.service;

import java.util.List;

import com.example.dto.response.ProjectResponse;
import com.example.job_portal_resume_service.payload.AddProjectRequest;

public interface ProjectService {

    ProjectResponse addProject(
            Long resumeId,
            Long candidateId,
            AddProjectRequest req) throws Exception;

    List<ProjectResponse> getAllProjects(Long resumeId);

    ProjectResponse updateProject(
            Long projectId, Long resumeId,
            Long candidateId,
            AddProjectRequest req) throws Exception;

    void deleteProject(Long projectId, Long resumeId, Long candidateId) throws Exception;
}
