package com.example.job_portal_ai_service.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResumeImprovementRequest {

    @NotBlank(message = "Resume content is required")
    private String resumeContent;

    private String targetJobTitle;
}
