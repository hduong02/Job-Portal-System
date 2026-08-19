package com.example.job_portal_resume_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.job_portal_resume_service.model.Resume;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByCandidateIdAndIsActiveTrue(Long candidateId);

    Page<Resume> findByCandidateIdAndIsActiveTrue(Long candidateId, Pageable pageable);

    Optional<Resume> findByCandidateIdAndIsDefaultTrue(Long resumeId);

}
