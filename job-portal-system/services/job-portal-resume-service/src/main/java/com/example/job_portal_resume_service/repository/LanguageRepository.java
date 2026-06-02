package com.example.job_portal_resume_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_resume_service.model.Language;

import java.util.List;

public interface LanguageRepository extends JpaRepository<Language, Long> {

    List<Language> findByResume_IdOrderByDisplayOrderAsc(Long resumeId);
}
