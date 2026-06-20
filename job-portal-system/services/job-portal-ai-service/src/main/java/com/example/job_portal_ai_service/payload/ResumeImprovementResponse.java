package com.example.job_portal_ai_service.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
public class ResumeImprovementResponse {

    private int overallScore;
    private List<Improvement> improvements;
    private List<String> strengths;
    private String summary;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Improvement {
        private String section;
        private String issue;
        private String suggestion;
        private String priority;
    }
}

