package com.example.job_portal_job_service.service;

import java.util.List;

import com.example.dto.response.JobCategoryResponse;
import com.example.job_portal_job_service.model.JobCategory;
import com.example.job_portal_job_service.payload.JobCategoryRequest;

public interface JobCategoryService {

    JobCategoryResponse createCategory(JobCategoryRequest req) throws Exception;

    List<JobCategoryResponse> getAllCategories();

    JobCategoryResponse getCategoryById(Long id) throws Exception;

    JobCategoryResponse updateCategory(Long id, JobCategoryRequest req) throws Exception;

    void deleteCategory(Long id) throws Exception;
    
    JobCategory getCategoryEntityById(Long id) throws Exception;
}
