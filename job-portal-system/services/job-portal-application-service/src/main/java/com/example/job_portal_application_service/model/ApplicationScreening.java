package com.example.job_portal_application_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.example.domain.AiShortListStatus;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationScreening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long applicationId;

    @Column(nullable = false)
    private Integer overallScore;

    private Integer skillsMatchScore;

    private Integer experienceMatchScore;

    private Integer educationMatchScore;

    private AiShortListStatus shortListStatus;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> matchedSkills;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> missingSkills;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> concerns;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> strengths;

    @CreationTimestamp
    private LocalDateTime screenedAt;

    @Builder.Default
    private String screeningVersion="v1";
}
