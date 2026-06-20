package com.example.job_portal_ai_service.payload;

import lombok.Data;

import java.util.List;

@Data
public class ResumeSummaryRequest {

    private String targetJobTitle;
    private List<WorkExperienceInfo> workExperiences;
    private List<String> skills;
    private List<EducationInfo> educations;
    private Integer yearOfExperience;

    @Data
    public static class WorkExperienceInfo {
        private String jobTitle;
        private String company;
        private String description;

    }
    @Data
    public static class EducationInfo {
        private String degree;
        private String fieldOfStudy;
        private String institutionName;
    }
}