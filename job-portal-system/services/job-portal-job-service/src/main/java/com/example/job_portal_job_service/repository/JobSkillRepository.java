package com.example.job_portal_job_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_job_service.model.JobSkill;

import java.util.List;

public interface JobSkillRepository extends JpaRepository<JobSkill, Long> {

    List<JobSkill> findByActiveTrue();
    boolean existsByName(String name);
    boolean existsBySlug(String slug);
}
