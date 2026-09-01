package com.example.job_portal_application_service.payload;

import com.example.domain.ApplicationStatus;
import java.time.LocalDateTime;

public record ApplicationStatusResponse(Long id, ApplicationStatus status, LocalDateTime updatedAt) {
}
