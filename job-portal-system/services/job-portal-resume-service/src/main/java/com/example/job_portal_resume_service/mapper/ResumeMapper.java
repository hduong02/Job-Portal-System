package com.example.job_portal_resume_service.mapper;

import java.util.List;

import com.example.dto.response.EducationResponse;
import com.example.dto.response.LanguageResponse;
import com.example.dto.response.PersonalInfoResponse;
import com.example.dto.response.ProjectResponse;
import com.example.dto.response.ResumeResponse;
import com.example.dto.response.ResumeSkillResponse;
import com.example.dto.response.WorkExperienceResponse;
import com.example.job_portal_resume_service.model.Education;
import com.example.job_portal_resume_service.model.Language;
import com.example.job_portal_resume_service.model.PersonalInfo;
import com.example.job_portal_resume_service.model.Project;
import com.example.job_portal_resume_service.model.Resume;
import com.example.job_portal_resume_service.model.ResumeSkill;

public class ResumeMapper {

    public static PersonalInfoResponse toPersonalInfoResponse(PersonalInfo personalInfo) {

        if (personalInfo == null) return null;

        return PersonalInfoResponse.builder()
                .firstName(personalInfo.getFirstName())
                .lastName(personalInfo.getLastName())
                .email(personalInfo.getEmail())
                .phone(personalInfo.getPhone())
                .headline(personalInfo.getHeadline())
                .profileImage(personalInfo.getProfileImage())
                .country(personalInfo.getCountry())
                .city(personalInfo.getCity())
                .githubUrl(personalInfo.getGithubUrl())
                .linkedinUrl(personalInfo.getLinkedinUrl())
                .portfolioUrl(personalInfo.getPortfolioUrl())
                .websiteUrl(personalInfo.getWebsiteUrl())
                .build();
    }

    public static ResumeResponse toResponse(
            Resume resume,
            List<WorkExperienceResponse> workExperiences,
            List<EducationResponse> educations,
            List<ResumeSkillResponse> skills,
            List<ProjectResponse> projects,
            List<LanguageResponse> languages) {

        if (resume == null) return null;

        return ResumeResponse.builder()
                .id(resume.getId())
                .candidateId(resume.getCandidateId())
                .title(resume.getTitle())
                .template(resume.getTemplate())
                .visibility(resume.getVisibility())
                .isDefault(resume.getIsDefault())
                .personalInfo(ResumeMapper.toPersonalInfoResponse(resume.getPersonalInfo()))
                .summary(resume.getSummary())
                .completionScore(resume.getCompletionScore())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .workExperiences(workExperiences)
                .educations(educations)
                .skills(skills)
                .projects(projects)
                .languages(languages)
                .build();
    }

    public static ResumeSkillResponse toSkillResponse(ResumeSkill skill) {
        if (skill == null) return null;
        return ResumeSkillResponse.builder()
                .id(skill.getId())
                .skillName(skill.getSkillName())
                .proficiencyLevel(skill.getProficiencyLevel())
                .yearsOfExperience(skill.getYearsOfExperience())
                .displayOrder(skill.getDisplayOrder())
                .build();
    }

    public static EducationResponse toEducationResponse(Education edu) {
        if (edu == null) return null;
        return EducationResponse.builder()
                .id(edu.getId())
                .institutionName(edu.getInstitutionName())
                .degree(edu.getDegree())
                .fieldOfStudy(edu.getFieldOfStudy())
                .grade(edu.getGrade())
                .startDate(edu.getStartDate())
                .endDate(edu.getEndDate())
                .isCurrentlyStudying(edu.getIsCurrentlyStudying())
                .description(edu.getDescription())
                .displayOrder(edu.getDisplayOrder())
                .build();
    }

    public static ProjectResponse toProjectResponse(Project project){
        if (project == null) return null;

        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .technologies(project.getTechnologies())
                .projectUrl(project.getProjectUrl())
                .sourceCodeUrl(project.getSourceCodeUrl())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .isOngoing(project.getIsOngoing())
                .displayOrder(project.getDisplayOrder())
                .build();
    }

    public static LanguageResponse toLanguageResponse(Language lang){
        if (lang == null) return null;
        return LanguageResponse.builder()
                .id(lang.getId())
                .languageName(lang.getLanguageName())
                .proficiency(lang.getProficiency())
                .displayOrder(lang.getDisplayOrder())
                .build();
    }
}
