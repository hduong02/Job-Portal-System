package com.example.job_portal_ai_service.payload;

import lombok.Data;

import java.util.List;

@Data
public class SearchEnhanceResponse {

    private List<String> keywords;
    private List<String> locations;
    private List<String> jobTypes;
    private List<String> workModes;
    private List<String> experienceLevels;
    private Long minSalary;
    private List<String> skills;
}
