package com.example.job_portal_resume_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_resume_service.model.Education;

import java.util.List;

public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByResume_IdOrderByDisplayOrderAsc(Long resumeId);
}
