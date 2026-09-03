package com.example.job_portal_application_service.service;

import com.example.domain.AiShortListStatus;
import com.example.dto.response.JobResponse;
import com.example.dto.response.ResumeResponse;
import com.example.job_portal_application_service.client.AiClient;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.event.ApplicationEventPublisher;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.payload.ScreeningScoreResponse;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.service.impl.ApplicationServiceImpl;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = ScreeningReadPersistenceTest.Config.class, properties = {
        "spring.config.location=optional:classpath:/screening-read-test-empty.properties",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false", "spring.cloud.compatibility-verifier.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:screening-read;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"
})
class ScreeningReadPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableAsync
    @EntityScan(basePackageClasses = Application.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationRepository.class)
    @Import({ApplicationServiceImpl.class, ApplicationScreeningService.class, ApplicationStatusUpdater.class})
    static class Config {
        // Pin the worker's commit between the two response reads without sleeps or timing luck.
        @Bean(name = "screeningExecutor") SyncTaskExecutor executor() { return new SyncTaskExecutor(); }
        @Bean JobClient jobs() { return mock(JobClient.class); }
        @Bean ResumeClient resumes() { return mock(ResumeClient.class); }
        @Bean AiClient ai() { return mock(AiClient.class); }
        @Bean CompanyClient companies() { return mock(CompanyClient.class); }
        @Bean UserClient users() { return mock(UserClient.class); }
        @Bean ApplicationEventPublisher publisher() { return mock(ApplicationEventPublisher.class); }
    }

    @Autowired ApplicationRepository applications;
    @Autowired ApplicationServiceImpl service;
    @Autowired ApplicationScreeningService worker;
    @Autowired JobClient jobs;
    @Autowired ResumeClient resumes;
    @Autowired AiClient ai;

    @Test
    void workerCommitsDuringResponseAssemblyWithoutReturningMixedAiState() throws Exception {
        var fixture = applications.saveAndFlush(Application.builder().candidateId(2L).employerId(3L)
                .jobId(5L).companyId(6L).resumeId(7L).build());
        when(resumes.getResumeById(7L, 2L)).thenReturn(ResumeResponse.builder().build());
        var result = new ScreeningScoreResponse();
        result.setScore(85);
        result.setSkillsMatchScore(90);
        result.setExperienceMatchScore(85);
        result.setEducationMatchScore(75);
        result.setSummary("Completed screening");
        result.setMatchedSkills(List.of("Java"));
        result.setMissingSkills(List.of());
        result.setStrengths(List.of());
        result.setConcerns(List.of());
        when(ai.scoreCandidate(any())).thenReturn(result);
        var committed = new AtomicBoolean();
        when(jobs.getJobById(5L)).thenAnswer(invocation -> {
            if (committed.compareAndSet(false, true)) {
                // The service has already loaded the pending Application. The real
                // worker now commits both rows before the screening repository read.
                worker.screenAsync(fixture.getId(), 2L, 5L, 7L);
            }
            return JobResponse.builder().employerId(3L).build();
        });

        var response = service.getApplicationById(fixture.getId(), 3L);
        var persisted = applications.findById(fixture.getId()).orElseThrow();
        assertEquals(85, persisted.getAiScore(), "Worker persisted application fields successfully");
        assertEquals(AiShortListStatus.REVIEW_RECOMMENDED, persisted.getAiShortListStatus());
        assertEquals(85, response.getScreening().getOverallScore());
        assertAll(
                () -> assertEquals(response.getScreening().getOverallScore(), response.getAiScore()),
                () -> assertEquals(response.getScreening().getShortListStatus(), response.getAiShortListStatus()));
    }
}
