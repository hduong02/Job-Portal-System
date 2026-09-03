package com.example.job_portal_application_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.example.domain.ApplicationStatus;
import com.example.job_portal_application_service.event.ApplicationStatusChange;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database work only: remote notification lookups run after this proxy commits. */
@Service
@RequiredArgsConstructor
public class ApplicationStatusUpdater {
    private final ApplicationRepository applications;

    @Transactional(rollbackFor = Exception.class)
    public ApplicationStatusChange updateStatus(Long applicationId, Long employerId,
            ApplicationStatus status) throws Exception {
        var application = applications.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (!application.getEmployerId().equals(employerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the employer for this application");
        }
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate have already withdrawn");
        }
        var oldStatus = application.getStatus();
        application.setStatus(status);
        // The entity is managed. Flush once to obtain @UpdateTimestamp without merging a detached entity.
        applications.flush();
        return new ApplicationStatusChange(application.getId(), application.getCandidateId(),
                application.getJobId(), application.getCompanyId(), oldStatus,
                application.getStatus(), application.getUpdatedAt());
    }
}
