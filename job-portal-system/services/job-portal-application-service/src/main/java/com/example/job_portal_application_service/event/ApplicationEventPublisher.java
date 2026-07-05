package com.example.job_portal_application_service.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.domain.ApplicationStatus;
import com.example.dto.response.CompanyResponse;
import com.example.dto.response.JobResponse;
import com.example.dto.response.UserResponse;
import com.example.event.ApplicationStatusChangedEvent;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.model.Application;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApplicationEventPublisher {

    public static final String TOPIC = "application.status.changed";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserClient userClient;
    private final JobClient jobClient;
    private final CompanyClient companyClient;

    public void publishStatusChange(
            Application app,
            ApplicationStatus oldStatus,
            ApplicationStatus newStatus,
            String note) {
        try {
            UserResponse candidate = userClient.getUserById(app.getCandidateId());
            JobResponse job = jobClient.getJobById(app.getJobId());
            CompanyResponse company = companyClient.getCompanyById(app.getCompanyId());

            ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.builder()
                    .applicationId(app.getId())
                    .candidateId(app.getCandidateId())
                    .candidateEmail(candidate.getEmail())
                    .candidateName(candidate.getFullName())
                    .oldStatus(oldStatus)
                    .newStatus(app.getStatus())
                    .note(note)
                    .jobTitle(job.getTitle())
                    .companyName(company.getName())
                    .changedAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(TOPIC, String.valueOf(app.getId()), event);
        } catch (Exception e) {
            System.out.println("Error in publishStatusChange: " + e.getMessage());
        }
    }
}