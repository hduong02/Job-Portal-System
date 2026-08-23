package com.example.job_portal_application_service.service;

import com.example.domain.JobStatus;
import com.example.dto.response.JobResponse;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.event.ApplicationEventPublisher;
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
    private final ApplicationServiceImpl service = new ApplicationServiceImpl(
            mock(ApplicationEventPublisher.class), applications,
            mock(ApplicationNoteRepository.class), mock(ApplicationScreeningService.class),
            mock(ApplicationScreeningRepository.class), jobs,
            resumes, mock(CompanyClient.class), mock(UserClient.class));

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
}
