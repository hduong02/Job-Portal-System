package com.example.job_portal_application_service.event;

import com.example.domain.ApplicationStatus;
import com.example.dto.response.UserResponse;
import com.example.event.ApplicationStatusChangedEvent;
import com.example.job_portal_application_service.client.UserClient;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationEventPublisherTest {
    @Test
    void publishesTheCommittedSnapshotUsingTheExistingEventContract() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        var users = mock(UserClient.class);
        var details = mock(NotificationDetailsCache.class);
        when(users.getUserById(2L)).thenReturn(UserResponse.builder()
                .email("candidate@example.com").fullName("Candidate").build());
        when(details.jobTitle(5L)).thenReturn("Engineer");
        when(details.companyName(6L)).thenReturn("Company");
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        var change = new ApplicationStatusChange(1L, 2L, 5L, 6L, ApplicationStatus.PENDING,
                ApplicationStatus.SHORTLISTED, LocalDateTime.of(2026, 10, 9, 12, 0));
        new ApplicationEventPublisher(kafka, users, details).publishStatusChange(change, "Status changed");
        var event = ArgumentCaptor.forClass(ApplicationStatusChangedEvent.class);
        verify(kafka).send(eq(ApplicationEventPublisher.TOPIC), eq("1"), event.capture());
        assertEquals(1L, event.getValue().getApplicationId());
        assertEquals(2L, event.getValue().getCandidateId());
        assertEquals("candidate@example.com", event.getValue().getCandidateEmail());
        assertEquals("Candidate", event.getValue().getCandidateName());
        assertEquals(ApplicationStatus.PENDING, event.getValue().getOldStatus());
        assertEquals(ApplicationStatus.SHORTLISTED, event.getValue().getNewStatus());
        assertEquals("Engineer", event.getValue().getJobTitle());
        assertEquals("Company", event.getValue().getCompanyName());
        assertEquals("Status changed", event.getValue().getNote());
        assertNotNull(event.getValue().getChangedAt());
    }
}
