package com.example.job_portal_job_service.mapper;

import com.example.dto.response.JobTagResponse;
import com.example.job_portal_job_service.model.JobTag;

public class JobTagMapper {

    public static JobTagResponse toTagResponse(JobTag jobTag) {
        return JobTagResponse.builder()
                .id(jobTag.getId())
                .name(jobTag.getName())
                .slug(jobTag.getSlug())
                .build();
    }
}