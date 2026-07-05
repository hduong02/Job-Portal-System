package com.example.job_portal_notification_service.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.event.ApplicationStatusChangedEvent;
import com.example.job_portal_notification_service.service.EmailNotificationService;

@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final EmailNotificationService emailService;

    @KafkaListener(
            topics = "application.status.changed",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleStatusChanged(ApplicationStatusChangedEvent event) throws Exception {
        emailService.sendStatusChangedEmail(event);
    }
}
