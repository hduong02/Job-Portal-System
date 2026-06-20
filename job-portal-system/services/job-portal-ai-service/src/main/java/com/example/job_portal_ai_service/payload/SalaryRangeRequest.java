package com.example.job_portal_ai_service.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SalaryRangeRequest {

    @NotBlank(message = "Job title is required")
    private String title;

    private List<String> skills;
    private String experienceLevel;
    private String jobType;
    private String location;
}
