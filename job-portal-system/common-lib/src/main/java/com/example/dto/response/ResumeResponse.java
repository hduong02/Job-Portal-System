package com.example.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import com.example.domain.ResumeTemplate;
import com.example.domain.ResumeVisibility;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ResumeResponse {

    private Long id;
    private Long candidateId;
    private String title;
    private ResumeTemplate template;
    private ResumeVisibility visibility;
    private Boolean isDefault;
    private PersonalInfoResponse personalInfo;
    private String summary;
    private Integer completionScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<WorkExperienceResponse> workExperiences;
    private List<EducationResponse> educations;
    private List<ResumeSkillResponse> skills;
    private List<ProjectResponse> projects;
    private List<LanguageResponse> languages;

}