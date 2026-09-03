package com.example.job_portal_application_service.service;

import com.example.domain.AiShortListStatus;
import com.example.dto.response.ApplicationResponse;
import com.example.dto.response.CompanyResponse;
import com.example.dto.response.JobResponse;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.event.ApplicationEventPublisher;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.model.ApplicationScreening;
import com.example.job_portal_application_service.payload.CompanyApplicationFilterRequest;
import com.example.job_portal_application_service.repository.ApplicationNoteRepository;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.repository.ApplicationScreeningRepository;
import com.example.job_portal_application_service.service.impl.ApplicationServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationAiResponseTest {
    enum ReadPath { DETAIL, CANDIDATE_LIST, JOB_LIST, COMPANY_LIST }

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ApplicationScreeningRepository screenings = mock(ApplicationScreeningRepository.class);
    private final JobClient jobs = mock(JobClient.class);
    private final CompanyClient companies = mock(CompanyClient.class);
    private final ApplicationServiceImpl service = new ApplicationServiceImpl(
            mock(ApplicationEventPublisher.class), mock(ApplicationStatusUpdater.class), applications,
            mock(ApplicationNoteRepository.class), mock(ApplicationScreeningService.class), screenings,
            jobs, mock(ResumeClient.class), companies, mock(UserClient.class));
    private final Application application = Application.builder().id(1L).candidateId(2L)
            .employerId(3L).jobId(5L).companyId(6L).build();
    private final PageRequest page = PageRequest.of(0, 10);

    static Stream<Arguments> completedScreenings() {
        return Stream.of(
                Arguments.of(0, AiShortListStatus.LOW_MATCH),
                Arguments.of(49, AiShortListStatus.LOW_MATCH),
                Arguments.of(50, AiShortListStatus.PENDING_REVIEW),
                Arguments.of(74, AiShortListStatus.PENDING_REVIEW),
                Arguments.of(75, AiShortListStatus.REVIEW_RECOMMENDED),
                Arguments.of(88, AiShortListStatus.REVIEW_RECOMMENDED),
                Arguments.of(89, AiShortListStatus.REVIEW_RECOMMENDED),
                Arguments.of(90, AiShortListStatus.AUTO_SHORTLISTED),
                Arguments.of(100, AiShortListStatus.AUTO_SHORTLISTED))
                .flatMap(result -> Stream.of(ReadPath.values()).map(path ->
                        Arguments.of(result.get()[0], result.get()[1], path)));
    }

    @ParameterizedTest
    @MethodSource("completedScreenings")
    void completedScreeningAgreesWithApplicationAiFields(int score, AiShortListStatus status,
                                                        ReadPath path) throws Exception {
        application.setAiScore(score);
        application.setAiShortListStatus(status);
        when(screenings.findByApplicationId(1L)).thenReturn(ApplicationScreening.builder()
                .applicationId(1L).overallScore(score).shortListStatus(status)
                .skillsMatchScore(90).experienceMatchScore(85).educationMatchScore(75).build());

        ApplicationResponse response = read(path);

        assertNotNull(response.getScreening());
        assertEquals(score, response.getScreening().getOverallScore());
        assertEquals(status, response.getScreening().getShortListStatus());
        assertAll(
                () -> assertEquals(response.getScreening().getOverallScore(), response.getAiScore()),
                () -> assertEquals(response.getScreening().getShortListStatus(), response.getAiShortListStatus()));
    }

    @ParameterizedTest
    @EnumSource(ReadPath.class)
    void screeningCommittedAfterApplicationReadKeepsResponseConsistent(ReadPath path) throws Exception {
        // The application was loaded before the async worker committed. Its screening
        // is queried later, after the remote job/company/user calls, and is now visible.
        when(screenings.findByApplicationId(1L)).thenReturn(ApplicationScreening.builder()
                .applicationId(1L).overallScore(85)
                .skillsMatchScore(90).experienceMatchScore(85).educationMatchScore(75)
                .shortListStatus(AiShortListStatus.REVIEW_RECOMMENDED).build());

        ApplicationResponse response = read(path);

        assertAll(
                () -> assertEquals(response.getScreening().getOverallScore(), response.getAiScore()),
                () -> assertEquals(response.getScreening().getShortListStatus(), response.getAiShortListStatus()));
        assertNull(application.getAiScore(), "Reading a response must not modify the stale entity");
    }

    @ParameterizedTest
    @EnumSource(ReadPath.class)
    void completedScreeningOverridesAnOlderApplicationScore(ReadPath path) throws Exception {
        application.setAiScore(49);
        application.setAiShortListStatus(AiShortListStatus.LOW_MATCH);
        when(screenings.findByApplicationId(1L)).thenReturn(ApplicationScreening.builder()
                .applicationId(1L).overallScore(85)
                .skillsMatchScore(90).experienceMatchScore(85).educationMatchScore(75)
                .shortListStatus(AiShortListStatus.REVIEW_RECOMMENDED).build());

        ApplicationResponse response = read(path);

        assertEquals(85, response.getAiScore());
        assertEquals(AiShortListStatus.REVIEW_RECOMMENDED, response.getAiShortListStatus());
        assertEquals(49, application.getAiScore(), "Reading must not persist a stale entity");
    }

    @ParameterizedTest
    @EnumSource(ReadPath.class)
    void pendingScreeningKeepsAiFieldsNull(ReadPath path) throws Exception {
        ApplicationResponse response = read(path);
        assertNull(response.getScreening());
        assertNull(response.getAiScore());
        assertNull(response.getAiShortListStatus());
    }

    private ApplicationResponse read(ReadPath path) throws Exception {
        when(jobs.getJobById(5L)).thenReturn(JobResponse.builder().employerId(3L).build());
        when(companies.getMyCompany(3L)).thenReturn(CompanyResponse.builder().id(6L).build());
        var results = new PageImpl<>(List.of(application));
        return switch (path) {
            case DETAIL -> {
                when(applications.findById(1L)).thenReturn(Optional.of(application));
                yield service.getApplicationById(1L, 3L);
            }
            case CANDIDATE_LIST -> {
                when(applications.findByCandidateId(2L, page)).thenReturn(results);
                yield service.getMyApplications(2L, page).getContent().getFirst();
            }
            case JOB_LIST -> {
                when(applications.findByJobId(5L, page)).thenReturn(results);
                yield service.getApplicationsForJob(5L, 3L, page).getContent().getFirst();
            }
            case COMPANY_LIST -> {
                when(applications.findAll(org.mockito.ArgumentMatchers.<Specification<Application>>any(), eq(page)))
                        .thenReturn(results);
                yield service.getApplicationsForCompany(3L, new CompanyApplicationFilterRequest(), page)
                        .getContent().getFirst();
            }
        };
    }
}
