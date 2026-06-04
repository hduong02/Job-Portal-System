package com.example.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.example.domain.AiShortListStatus;
import com.example.domain.ApplicationStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationResponse {

    private Long id;
    private UserResponse candidate;
    private Long employerId;

    private JobResponse job;
    private CompanyResponse company;

    private ApplicationStatus status;

    // Submission content
    private Long resumeId;
    private String coverLetter;

    // Candidate preferences
    private BigDecimal expectedSalary;
    private LocalDate availableFrom;

    // Tracking
    private Boolean isStarred;

    private List<ApplicationNoteResponse> notes;

    // Withdrawal
    private LocalDateTime withdrawnAt;
    private String withdrawnReason;


    private Integer aiScore;

    private AiShortListStatus aishortListStatus;

    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

}
