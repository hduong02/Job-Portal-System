package com.example.job_portal_preference_service.mapper;

import com.example.dto.response.SavedJobResponse;
import com.example.job_portal_preference_service.model.SavedJob;

public class PreferenceMapper {

    public static SavedJobResponse toSavedJobResponse(SavedJob savedJob) {

        return SavedJobResponse.builder()
                .id(savedJob.getId())
                .candidateId(savedJob.getCandidateId())
                .jobId(savedJob.getJobId())
                .savedAt(savedJob.getSavedAt())
                .build();
    }
}