package com.example.job_portal_ai_service.payload;

import lombok.Data;

import java.util.List;

@Data
public class JobAlertSuggestResponse {

    private List<String> suggestedKeywords;
    private List<String> suggestedLocations;
    private List<String> suggestedJobTypes;
    private List<String> suggestedWorkModes;
    private List<String> suggestedExperienceLevels;
    private List<String> suggestedIndustries;
    private String reasoning;
}
