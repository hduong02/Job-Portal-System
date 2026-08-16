package com.example.job_portal_application_service.payload;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ScreeningScoreRequest {
    private String jobTitle;
    private String experienceLevel;
    private List<String> requiredSkills;
    private String responsibilities;

    private String candidateSummary;
    private List<String> candidateSkills;
    private List<String> candidateExperience;
    private List<String> candidateEducation;
}
