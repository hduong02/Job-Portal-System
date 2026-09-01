package com.example.job_portal_application_service.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.dto.response.UserResponse;
import com.example.event.ApplicationStatusChangedEvent;
import com.example.job_portal_application_service.client.UserClient;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApplicationEventPublisher {

    public static final String TOPIC = "application.status.changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserClient userClient;
    private final NotificationDetailsCache notificationDetailsCache;

    public void publishStatusChange(
            ApplicationStatusChange change,
            String note) {
        try {
            UserResponse candidate = userClient.getUserById(change.candidateId());
            String jobTitle = notificationDetailsCache.jobTitle(change.jobId());
            String companyName = notificationDetailsCache.companyName(change.companyId());

            ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.builder()
                    .applicationId(change.applicationId())
                    .candidateId(change.candidateId())
                    .candidateEmail(candidate.getEmail())
                    .candidateName(candidate.getFullName())
                    .oldStatus(change.oldStatus())
                    .newStatus(change.newStatus())
                    .note(note)
                    .jobTitle(jobTitle)
                    .companyName(companyName)
                    .changedAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(TOPIC, String.valueOf(change.applicationId()), event);
        } catch (Exception e) {
            System.out.println("Error in publishStatusChange: " + e.getMessage());
        }
    }
}