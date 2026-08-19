package com.example.job_portal_preference_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.job_portal_preference_service.model.SavedJob;

import java.util.List;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    List<SavedJob> findByCandidateId(Long candidateId);

    Page<SavedJob> findByCandidateId(Long candidateId, Pageable pageable);
    
    boolean existsByCandidateIdAndJobId(Long candidateId, Long jobId);
}
