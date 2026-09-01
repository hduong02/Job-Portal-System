package com.example.job_portal_application_service.service;

import com.example.domain.ApplicationStatus;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.event.ApplicationEventPublisher;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.repository.ApplicationNoteRepository;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.repository.ApplicationScreeningRepository;
import com.example.job_portal_application_service.service.impl.ApplicationServiceImpl;
import jakarta.persistence.EntityManagerFactory;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = StatusUpdatePersistenceTest.Config.class, properties = {
        "spring.config.location=optional:classpath:/status-update-test-empty.properties",
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false", "spring.cloud.compatibility-verifier.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:status-update;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=500",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class StatusUpdatePersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Application.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationRepository.class)
    @Import(ApplicationStatusUpdater.class)
    static class Config {
        @Bean ApplicationEventPublisher publisher() { return mock(ApplicationEventPublisher.class); }
        @Bean ApplicationServiceImpl service(ApplicationRepository repository,
                ApplicationEventPublisher publisher, ApplicationStatusUpdater updater) {
            return new ApplicationServiceImpl(publisher, updater, repository, mock(ApplicationNoteRepository.class),
                    mock(ApplicationScreeningService.class), mock(ApplicationScreeningRepository.class),
                    mock(JobClient.class), mock(ResumeClient.class), mock(CompanyClient.class), mock(UserClient.class));
        }
    }

    @Autowired ApplicationServiceImpl service;
    @Autowired ApplicationRepository applications;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired HikariDataSource dataSource;
    private Application fixture;

    @BeforeEach
    void setup() {
        reset(publisher);
        fixture = applications.saveAndFlush(Application.builder().candidateId(2L).employerId(3L)
                .jobId(5L).companyId(6L).resumeId(7L).status(ApplicationStatus.PENDING).build());
        statistics().clear();
    }

    private org.hibernate.stat.Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void updatesWithOneSelectAndOneUpdateAndPublishesAfterCommit() throws Exception {
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Notification work must not hold the database transaction open");
            assertEquals(2, statistics().getPrepareStatementCount(),
                    "Status update should use one SELECT and one UPDATE");
            assertEquals(ApplicationStatus.SHORTLISTED,
                    applications.findById(fixture.getId()).orElseThrow().getStatus());
            return null;
        }).when(publisher).publishStatusChange(any(), anyString());

        var result = service.updateStatus(fixture.getId(), 3L, ApplicationStatus.SHORTLISTED);
        assertEquals(fixture.getId(), result.id());
        assertEquals(ApplicationStatus.SHORTLISTED, result.status());
        assertNotNull(result.updatedAt());
        assertEquals(result.updatedAt(), applications.findById(fixture.getId()).orElseThrow().getUpdatedAt());
        verify(publisher).publishStatusChange(any(), anyString());
    }

    @Test
    void blockedNotificationDoesNotPreventAnotherUpdateUsingTheOnlyConnection() throws Exception {
        var second = applications.saveAndFlush(Application.builder().candidateId(2L).employerId(3L)
                .jobId(5L).companyId(6L).resumeId(7L).status(ApplicationStatus.PENDING).build());
        var firstPublishing = new CountDownLatch(1);
        var releaseFirstPublisher = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                assertEquals(1, dataSource.getMaximumPoolSize());
                assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections(),
                        "The physical pooled connection must be returned before notification work");
                firstPublishing.countDown();
                assertTrue(releaseFirstPublisher.await(10, TimeUnit.SECONDS));
            }
            return null;
        }).when(publisher).publishStatusChange(any(), anyString());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.updateStatus(fixture.getId(), 3L,
                    ApplicationStatus.SHORTLISTED));
            try {
                assertTrue(firstPublishing.await(3, TimeUnit.SECONDS),
                        "First update must enter notification publishing");
                var next = executor.submit(() -> service.updateStatus(second.getId(), 3L,
                        ApplicationStatus.SHORTLISTED));
                assertEquals(ApplicationStatus.SHORTLISTED, next.get(3, TimeUnit.SECONDS).status(),
                        "Another update must finish while the first publisher remains blocked");
                assertFalse(first.isDone());
            } finally {
                releaseFirstPublisher.countDown();
            }
            assertEquals(ApplicationStatus.SHORTLISTED, first.get(3, TimeUnit.SECONDS).status());
        }
        verify(publisher, times(2)).publishStatusChange(any(), anyString());
    }

    @Test
    void rejectsUnrelatedEmployerWithoutUpdatingOrPublishing() {
        assertThrows(Exception.class, () -> service.updateStatus(fixture.getId(), 4L, ApplicationStatus.SHORTLISTED));
        assertEquals(0, statistics().getEntityUpdateCount());
        verifyNoInteractions(publisher);
        assertEquals(ApplicationStatus.PENDING, applications.findById(fixture.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsWithdrawnApplicationWithoutPublishing() {
        fixture.setStatus(ApplicationStatus.WITHDRAWN);
        applications.saveAndFlush(fixture);
        statistics().clear();
        assertThrows(Exception.class, () -> service.updateStatus(fixture.getId(), 3L, ApplicationStatus.SHORTLISTED));
        assertEquals(0, statistics().getEntityUpdateCount());
        verifyNoInteractions(publisher);
    }

    @Test
    void failedFlushRollsBackAndDoesNotPublish() {
        jdbc.execute("ALTER TABLE applications ADD CONSTRAINT test_status CHECK (status <> 'SHORTLISTED')");
        try {
            assertThrows(RuntimeException.class,
                    () -> service.updateStatus(fixture.getId(), 3L, ApplicationStatus.SHORTLISTED));
            verifyNoInteractions(publisher);
            assertEquals(ApplicationStatus.PENDING, applications.findById(fixture.getId()).orElseThrow().getStatus());
        } finally {
            jdbc.execute("ALTER TABLE applications DROP CONSTRAINT test_status");
        }
    }
}
