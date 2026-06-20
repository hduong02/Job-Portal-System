package com.example.job_portal_ai_service.payload;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CoverLetterRequest {

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    private String jobDescription;

    @NotBlank(message = "Candidate name is required")
    private String candidateName;

    private String candidateSummary;
    private List<String> candidateSkills;
    private List<String> candidateExperience;
    private String targetCompanyName;
}