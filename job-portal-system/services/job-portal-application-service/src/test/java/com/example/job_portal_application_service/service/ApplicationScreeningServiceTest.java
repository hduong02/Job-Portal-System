package com.example.job_portal_application_service.service;

import com.example.domain.AiShortListStatus;
import com.example.dto.response.JobResponse;
import com.example.dto.response.ResumeResponse;
import com.example.job_portal_application_service.client.AiClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.payload.ScreeningScoreResponse;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.repository.ApplicationScreeningRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationScreeningServiceTest {

    @Configuration
    @EnableAsync
    static class AsyncTestConfig {
    }

    @Test
    void queuesScreeningThroughSpringProxyAndPersistsResultsWhenExecuted() {
        JobClient jobs = mock(JobClient.class);
        ResumeClient resumes = mock(ResumeClient.class);
        AiClient ai = mock(AiClient.class);
        ApplicationRepository applications = mock(ApplicationRepository.class);
        ApplicationScreeningRepository screenings = mock(ApplicationScreeningRepository.class);
        Queue<Runnable> tasks = new ArrayDeque<>();
        Application application = Application.builder().id(1L).build();
        ScreeningScoreResponse score = new ScreeningScoreResponse();
        score.setScore(90);

        when(jobs.getJobById(3L)).thenReturn(JobResponse.builder().title("Engineer").build());
        when(resumes.getResumeById(4L, 2L)).thenReturn(ResumeResponse.builder().build());
        when(ai.scoreCandidate(any())).thenReturn(score);
        when(applications.findById(1L)).thenReturn(Optional.of(application));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncTestConfig.class);
            context.registerBean("screeningExecutor", Executor.class, () -> tasks::add);
            context.registerBean(ApplicationScreeningService.class,
                    () -> new ApplicationScreeningService(jobs, resumes, ai, applications, screenings));
            context.refresh();

            ApplicationScreeningService service = context.getBean(ApplicationScreeningService.class);
            assertDoesNotThrow(() -> service.screenAsync(1L, 2L, 3L, 4L));
            assertEquals(1, tasks.size());
            verifyNoInteractions(jobs, resumes, ai, screenings);

            tasks.remove().run();

            verify(screenings).save(argThat(screening ->
                    screening.getApplicationId().equals(1L)
                            && screening.getOverallScore() == 90
                            && screening.getShortListStatus() == AiShortListStatus.AUTO_SHORTLISTED));
            verify(applications).save(application);
            assertEquals(90, application.getAiScore());
            assertEquals(AiShortListStatus.AUTO_SHORTLISTED, application.getAiShortListStatus());
        }
    }
}
