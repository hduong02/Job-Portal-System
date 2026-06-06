package com.example.job_portal_preference_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_preference_service.model.SavedJob;

import java.util.List;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    List<SavedJob> findByCandidateId(Long candidateId);
    
    boolean existsByCandidateIdAndJobId(Long candidateId, Long jobId);
}
