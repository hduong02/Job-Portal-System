package com.example.job_portal_job_service.service;

import java.util.List;
import java.util.Set;

import com.example.dto.response.JobTagResponse;
import com.example.job_portal_job_service.model.JobTag;
import com.example.job_portal_job_service.payload.JobTagRequest;

public interface JobTagService {
    
    JobTagResponse createTag(JobTagRequest req) throws Exception;

    List<JobTagResponse> getAllTags();

    JobTagResponse getById(Long id) throws Exception;

    JobTagResponse updateTag(Long id, JobTagRequest req) throws Exception;

    void deleteTag(Long id) throws Exception;

    JobTag getTagEntityById(Long id) throws Exception;

    Set<JobTag> getTagsByIds(Set<Long> ids) throws Exception;
}

