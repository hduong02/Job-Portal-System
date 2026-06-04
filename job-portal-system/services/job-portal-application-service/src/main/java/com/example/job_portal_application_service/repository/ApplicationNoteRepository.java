package com.example.job_portal_application_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_application_service.model.ApplicationNote;

import java.util.List;

public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, Long> {

    List<ApplicationNote> findByApplicationId(Long applicationId);
}

