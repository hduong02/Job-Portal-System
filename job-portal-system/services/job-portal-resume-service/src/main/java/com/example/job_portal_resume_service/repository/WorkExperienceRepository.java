package com.example.job_portal_resume_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_resume_service.model.WorkExperience;

import java.util.List;

public interface WorkExperienceRepository extends JpaRepository<WorkExperience, Long> {

    List<WorkExperience> findByResume_IdOrderByDisplayOrderAsc(Long resumeId);
}
