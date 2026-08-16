package com.example.job_portal_application_service.payload;


import lombok.Data;

import java.util.List;

@Data
public class ScreeningScoreResponse {

    private int score;
    private int skillsMatchScore;
    private int experienceMatchScore;
    private int educationMatchScore;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> concerns;
    private String summary;
}
