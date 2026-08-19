package com.example.job_portal_preference_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.dto.response.SavedJobResponse;
import com.example.job_portal_preference_service.payload.SaveJobRequest;

public interface SavedJobService {

    SavedJobResponse saveJob(Long candidateId, SaveJobRequest req) throws Exception;

    void unsaveJob(Long candidateId, Long savedJobId) throws Exception;

    Page<SavedJobResponse> getSavedJob(Long candidateId, Pageable pageable);

    boolean isSaved(Long candidateId, Long jobId);
}
