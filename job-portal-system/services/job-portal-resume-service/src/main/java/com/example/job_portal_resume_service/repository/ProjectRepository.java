package com.example.job_portal_resume_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_resume_service.model.Project;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project>findByResume_IdOrderByDisplayOrderAsc(Long resumeId);
}
