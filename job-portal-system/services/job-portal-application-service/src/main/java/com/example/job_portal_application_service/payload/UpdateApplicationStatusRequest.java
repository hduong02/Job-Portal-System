package com.example.job_portal_application_service.payload;

import com.example.domain.ApplicationStatus;

import lombok.Data;

@Data
public class UpdateApplicationStatusRequest {

    private ApplicationStatus status;
}
