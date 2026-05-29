package com.example.job_portal_job_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_job_service.model.JobCategory;

import java.util.List;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Long> {

    boolean existsByName(String name);
    boolean existsBySlug(String slug);

    List<JobCategory> findByActiveTrue();
}

