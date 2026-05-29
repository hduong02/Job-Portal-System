package com.example.job_portal_job_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_job_service.model.JobTag;

public interface JobTagRepository extends JpaRepository<JobTag, Long> {

    boolean existsByName(String name);
    boolean existsBySlug(String slug);
}
