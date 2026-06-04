package com.example.job_portal_application_service.payload;

import com.example.domain.AiShortListStatus;
import com.example.domain.ApplicationStatus;

import lombok.Data;

@Data
public class CompanyApplicationFilterRequest {

    private Long jobId;

    private ApplicationStatus status;

    private Boolean isStarred = false;

    private AiShortListStatus aiShortListStatus;

    private Integer minAiScore;

    private String sortBy;
}
