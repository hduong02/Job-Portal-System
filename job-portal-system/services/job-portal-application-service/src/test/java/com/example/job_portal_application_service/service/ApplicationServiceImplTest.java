package com.example.job_portal_application_service.service;

import com.example.domain.JobStatus;
import com.example.domain.ApplicationStatus;
import com.example.dto.response.JobResponse;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.event.ApplicationEventPublisher;
import com.example.job_portal_application_service.event.ApplicationStatusChange;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.payload.CreateApplicationRequest;
import com.example.job_portal_application_service.repository.ApplicationNoteRepository;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.repository.ApplicationScreeningRepository;
import com.example.job_portal_application_service.service.impl.ApplicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ApplicationServiceImplTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final JobClient jobs = mock(JobClient.class);
    private final ResumeClient resumes = mock(ResumeClient.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ApplicationNoteRepository notes = mock(ApplicationNoteRepository.class);
    private final ApplicationScreeningRepository screenings = mock(ApplicationScreeningRepository.class);
    private final CompanyClient companies = mock(CompanyClient.class);
    private final UserClient users = mock(UserClient.class);
    private final ApplicationStatusUpdater statusUpdater = mock(ApplicationStatusUpdater.class);
    private final ApplicationServiceImpl service = new ApplicationServiceImpl(
            publisher, statusUpdater, applications, notes, mock(ApplicationScreeningService.class),
            screenings, jobs, resumes, companies, users);

    @Test
    void duplicateApplicationReturnsConflictBeforeDoingRemoteWork() {
        when(applications.existsByCandidateIdAndJobId(2L, 5L)).thenReturn(true);
        var request = CreateApplicationRequest.builder().jobId(5L).resumeId(7L).build();
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.createApplication(2L, request)).getStatusCode());
        verifyNoInteractions(jobs, resumes, publisher);
        verify(applications, never()).save(any());
    }

    @Test
    void unrelatedCandidateCannotWithdrawOrDelete() {
        when(applications.findById(1L)).thenReturn(Optional.of(
                Application.builder().id(1L).candidateId(2L).employerId(3L).build()));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.withdraw(1L, 4L, null)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.deleteApplication(1L, 4L)).getStatusCode());
        verify(applications, never()).save(any());
        verify(applications, never()).delete(any(Application.class));
    }

    @Test
    void rejectsJobsThatAreNotAcceptingApplications() {
        JobResponse[] unavailableJobs = {
                JobResponse.builder().status(JobStatus.DRAFT).active(true).build(),
                JobResponse.builder().status(JobStatus.CLOSED).active(false).build(),
                JobResponse.builder().status(JobStatus.EXPIRED).active(true).build(),
                JobResponse.builder().status(JobStatus.FILLED).active(true).build(),
                JobResponse.builder().status(JobStatus.OPEN).active(false).build(),
                JobResponse.builder().status(JobStatus.OPEN).active(true)
                        .applicationDeadline(LocalDate.now().minusDays(1)).build(),
                JobResponse.builder().status(JobStatus.OPEN).active(true)
                        .expiresAt(LocalDate.now().minusDays(1)).build()
        };
        CreateApplicationRequest request = CreateApplicationRequest.builder()
                .jobId(5L).resumeId(7L).build();

        for (JobResponse job : unavailableJobs) {
            when(jobs.getJobById(5L)).thenReturn(job);
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.createApplication(2L, request));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        verifyNoInteractions(resumes);
        verify(applications, never()).save(any());
    }

    @Test
    void detailRejectsUnrelatedUser() {
        when(applications.findById(1L)).thenReturn(Optional.of(
                Application.builder().id(1L).candidateId(2L).employerId(3L).build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getApplicationById(1L, 4L));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(jobs);
    }

    @Test
    void jobListRejectsAnotherEmployer() {
        when(jobs.getJobById(5L)).thenReturn(JobResponse.builder().employerId(3L).build());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getApplicationsForJob(5L, 4L, PageRequest.of(0, 10)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(applications);
    }
    @Test
    void statusUpdateReturnsCompactPatchWithoutRebuildingDetails() throws Exception {
        var change = new ApplicationStatusChange(1L, 2L, 5L, 6L, ApplicationStatus.PENDING,
                ApplicationStatus.SHORTLISTED, java.time.LocalDateTime.of(2026, 10, 9, 12, 0));
        when(statusUpdater.updateStatus(1L, 3L, ApplicationStatus.SHORTLISTED)).thenReturn(change);
        var result = service.updateStatus(1L, 3L, ApplicationStatus.SHORTLISTED);
        assertEquals(1L, result.id());
        assertEquals(ApplicationStatus.SHORTLISTED, result.status());
        assertEquals(change.updatedAt(), result.updatedAt());
        verify(publisher).publishStatusChange(change, "Your application status has changed");
        verifyNoInteractions(applications);
        verifyNoInteractions(jobs, companies, users, notes, screenings, resumes);
    }

    @Test
    void statusUpdateRejectsAnotherEmployerBeforeSavingOrPublishing() throws Exception {
        when(statusUpdater.updateStatus(1L, 4L, ApplicationStatus.SHORTLISTED))
                .thenThrow(new Exception("You are not the employer for this application"));
        assertThrows(Exception.class, () -> service.updateStatus(1L, 4L, ApplicationStatus.SHORTLISTED));
        verify(applications, never()).save(any());
        verifyNoInteractions(publisher);
    }
}
