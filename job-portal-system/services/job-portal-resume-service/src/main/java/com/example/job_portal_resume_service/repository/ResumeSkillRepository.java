package com.example.job_portal_resume_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_resume_service.model.ResumeSkill;

import java.util.List;

public interface ResumeSkillRepository extends JpaRepository<ResumeSkill, Long> {

    List<ResumeSkill> findByResume_IdOrderByDisplayOrderAsc(Long resumeId);
}
