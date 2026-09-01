package com.example.job_portal_application_service.event;

import com.example.domain.ApplicationStatus;
import com.example.job_portal_application_service.payload.ApplicationStatusResponse;
import java.time.LocalDateTime;

/** Values captured after flush, before the database transaction ends. */
public record ApplicationStatusChange(Long applicationId, Long candidateId, Long jobId,
        Long companyId, ApplicationStatus oldStatus, ApplicationStatus newStatus, LocalDateTime updatedAt) {
    public ApplicationStatusResponse response() {
        return new ApplicationStatusResponse(applicationId, newStatus, updatedAt);
    }
}
