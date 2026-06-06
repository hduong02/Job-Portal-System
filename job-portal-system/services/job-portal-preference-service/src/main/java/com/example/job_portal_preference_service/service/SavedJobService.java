package com.example.job_portal_preference_service.service;

import java.util.List;

import com.example.dto.response.SavedJobResponse;
import com.example.job_portal_preference_service.payload.SaveJobRequest;

public interface SavedJobService {

    SavedJobResponse saveJob(Long candidateId, SaveJobRequest req) throws Exception;

    void unsaveJob(Long candidateId, Long savedJobId) throws Exception;

    List<SavedJobResponse> getSavedJob(Long candidateId);

    boolean isSaved(Long candidateId, Long jobId);
}
