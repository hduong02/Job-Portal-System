package com.example.job_portal_notification_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.domain.ApplicationStatus;
import com.example.event.ApplicationStatusChangedEvent;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public void sendStatusChangedEmail(ApplicationStatusChangedEvent event) throws Exception {
        try {
            String statusLabel = STATUS_LABELS.getOrDefault(event.getNewStatus(),
                    event.getNewStatus().name());
            String statusColor = STATUS_COLORS.getOrDefault(event.getNewStatus(),
                    "#6b7280");
            String subject="Application update: " + event.getJobTitle() + " at"
                    + event.getCandidateName();
            String body = buildStatusChangeHtml(event, statusColor, statusLabel);
            String candidateEmail = event.getCandidateEmail();

            sendEmail(candidateEmail, subject, body);
        } catch (
                Exception e
        ) {
            throw new Exception(e.getMessage());
        }
    }

    private void sendEmail(String candidateEmail, String subject, String body)
            throws MessagingException {

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(
                mimeMessage,
                true, "UTF-8");
        mimeMessageHelper.setFrom(fromEmail);
        mimeMessageHelper.setTo(candidateEmail);
        mimeMessageHelper.setSubject(subject);
        mimeMessageHelper.setText(body,true);
        mailSender.send(mimeMessage);

    }

    private String buildStatusChangeHtml(
            ApplicationStatusChangedEvent event,
            String statusColor,
            String statusLabel
    ) {

        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f3f4f6;margin:0;padding:20px;'>"
                + "<div style='max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.1);'>"
                + "<div style='background:" + statusColor + ";padding:24px;text-align:center;'>"
                + "<h1 style='color:#fff;margin:0;font-size:22px;'>Application Status Update</h1>"
                + "</div>"
                + "<div style='padding:32px;'>"
                + "<p style='color:#374151;font-size:16px;'>Hi <strong>" + escapeHtml(event.getCandidateName()) + "</strong>,</p>"
                + "<p style='color:#6b7280;'>Your application for <strong>" + escapeHtml(event.getJobTitle())
                + "</strong> at <strong>" + escapeHtml(event.getCompanyName()) + "</strong> has been updated.</p>"
                + "<div style='background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;padding:20px;margin:20px 0;text-align:center;'>"
                + "<p style='margin:0 0 8px;color:#9ca3af;font-size:13px;text-transform:uppercase;letter-spacing:1px;'>New Status</p>"
                + "<span style='display:inline-block;background:" + statusColor + ";color:#fff;padding:8px 20px;"
                + "border-radius:20px;font-weight:bold;font-size:16px;'>" + escapeHtml(statusLabel) + "</span>"
                + "</div>"
                + "<p style='color:#9ca3af;font-size:13px;margin-top:24px;'>You can log in to the Job Portal to view your full application timeline.</p>"
                + "</div>"
                + "<div style='background:#f9fafb;padding:16px;text-align:center;border-top:1px solid #e5e7eb;'>"
                + "<p style='margin:0;color:#9ca3af;font-size:12px;'>Job Portal &mdash; You are receiving this because you applied for a job.</p>"
                + "</div>"
                + "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final Map<ApplicationStatus, String> STATUS_LABELS = Map.of(
            ApplicationStatus.PENDING, "Pending Review",
            ApplicationStatus.REVIEWING, "Under Review",
            ApplicationStatus.SHORTLISTED, "Shortlisted",
            ApplicationStatus.INTERVIEW_SCHEDULED, "Interview Scheduled",
            ApplicationStatus.REJECTED, "Not Selected",
            ApplicationStatus.HIRED, "Hired!",
            ApplicationStatus.WITHDRAWN, "Withdrawn"
    );

    private static final Map<ApplicationStatus, String> STATUS_COLORS = Map.of(
            ApplicationStatus.PENDING, "#f59e0b",
            ApplicationStatus.REVIEWING, "#3b82f6",
            ApplicationStatus.SHORTLISTED, "#8b5cf6",
            ApplicationStatus.INTERVIEW_SCHEDULED, "#06b6d4",
            ApplicationStatus.REJECTED, "#ef4444",
            ApplicationStatus.HIRED, "#22c55e",
            ApplicationStatus.WITHDRAWN, "#6b7280"
    );
}
