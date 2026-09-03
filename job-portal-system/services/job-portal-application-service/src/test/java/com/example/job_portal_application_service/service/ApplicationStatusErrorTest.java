package com.example.job_portal_application_service.service;

import com.example.domain.ApplicationStatus;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationStatusErrorTest {
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ApplicationStatusUpdater updater = new ApplicationStatusUpdater(applications);

    @Test
    void rejectsAnotherEmployerWithoutMutatingStatus() {
        var application = Application.builder().id(1L).employerId(3L)
                .status(ApplicationStatus.PENDING).build();
        when(applications.findById(1L)).thenReturn(Optional.of(application));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> updater.updateStatus(1L, 4L, ApplicationStatus.SHORTLISTED)).getStatusCode());
        assertEquals(ApplicationStatus.PENDING, application.getStatus());
        verify(applications, never()).flush();
    }

    @Test
    void withdrawnApplicationReturnsConflict() {
        when(applications.findById(1L)).thenReturn(Optional.of(Application.builder()
                .id(1L).employerId(3L).status(ApplicationStatus.WITHDRAWN).build()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> updater.updateStatus(1L, 3L, ApplicationStatus.SHORTLISTED)).getStatusCode());
        verify(applications, never()).flush();
    }

    @Test
    void missingApplicationReturnsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> updater.updateStatus(1L, 3L, ApplicationStatus.SHORTLISTED)).getStatusCode());
    }
}
