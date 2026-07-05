package com.example.event;


import com.example.domain.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationStatusChangedEvent {

    private Long applicationId;
    private Long candidateId;
    private String candidateEmail;
    private String candidateName;
    private ApplicationStatus oldStatus;
    private ApplicationStatus newStatus;
    private String note;
    private String jobTitle;
    private String companyName;
    private LocalDateTime changedAt;
}
