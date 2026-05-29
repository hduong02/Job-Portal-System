package com.example.job_portal_job_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.dto.response.JobCategoryResponse;
import com.example.job_portal_job_service.mapper.JobCategoryMapper;
import com.example.job_portal_job_service.model.JobCategory;
import com.example.job_portal_job_service.payload.JobCategoryRequest;
import com.example.job_portal_job_service.repository.JobCategoryRepository;
import com.example.job_portal_job_service.service.JobCategoryService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobCategoryServiceImpl implements JobCategoryService {

    private final JobCategoryRepository jobCategoryRepository;

    @Override
    public JobCategoryResponse createCategory(JobCategoryRequest req) throws Exception {

        if (jobCategoryRepository.existsByName(req.getName())) {
            throw new Exception("Category name already exist, choose different name");
        }

        JobCategory parent = null;
        if (req.getParentId() != null) {
            parent = getCategoryEntityById(req.getParentId());
        }
        String slug = generateUniqueSlug(req.getName());

        JobCategory category = JobCategory.builder()
                .name(req.getName())
                .slug(slug)
                .description(req.getDescription())
                .iconUrl(req.getIconUrl())
                .parent(parent)
                .active(true)
                .build();

        JobCategory saved = jobCategoryRepository.save(category);
        return JobCategoryMapper.toJobCategoryResponse(saved, true);
    }

    @Override
    public List<JobCategoryResponse> getAllCategories() {
        return jobCategoryRepository.findByActiveTrue().stream()
                .map(c -> JobCategoryMapper.toJobCategoryResponse(c, false))
                .collect(Collectors.toList());
    }

    @Override
    public JobCategoryResponse getCategoryById(Long id) throws Exception {
        JobCategory jobCategory = getCategoryEntityById(id);
        return JobCategoryMapper.toJobCategoryResponse(jobCategory, true);
    }

    @Override
    public JobCategoryResponse updateCategory(Long id, JobCategoryRequest req)
            throws Exception {
        JobCategory category = getCategoryEntityById(id);

        if (!category.getName().equals(req.getName()) &&
                jobCategoryRepository.existsByName(req.getName())) {
            throw new Exception("Category name already exist, choose different name");
        }

        JobCategory parent = null;
        if (req.getParentId() != null) {
            if (req.getParentId().equals(id)) {
                throw new Exception("A category cannot be its own parent");
            }
            parent = getCategoryEntityById(req.getParentId());
        }

        category.setName(req.getName());
        category.setDescription(req.getDescription());
        category.setIconUrl(req.getIconUrl());
        category.setParent(parent);

        JobCategory updated = jobCategoryRepository.save(category);
        return JobCategoryMapper.toJobCategoryResponse(updated, true);
    }

    @Override
    public void deleteCategory(Long id) throws Exception {
        JobCategory category = getCategoryEntityById(id);
        category.setActive(false);
        jobCategoryRepository.save(category);

    }

    @Override
    public JobCategory getCategoryEntityById(Long id) throws Exception {
        return jobCategoryRepository.findById(id).orElseThrow(
                () -> new Exception("Category not found")
        );
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]","")
                .trim().replaceAll("[\\s-]+","-");
        if (!jobCategoryRepository.existsBySlug(base)) {
            return base;
        }

        int counter = 1;
        while (jobCategoryRepository.existsBySlug(base + "-" + counter)) {
            counter ++;
        }
        return base + "-" + counter;
    }
}