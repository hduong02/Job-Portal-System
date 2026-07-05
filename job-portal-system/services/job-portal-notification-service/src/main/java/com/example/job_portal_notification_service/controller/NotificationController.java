package com.example.job_portal_notification_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.job_portal_notification_service.service.EmailNotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final EmailNotificationService emailNotificationService;

    @GetMapping("/sent")
    public String NotificationController() throws Exception {
        // emailNotificationService.sendStatusChangedEmail();
        return "email sent";
    }
}
