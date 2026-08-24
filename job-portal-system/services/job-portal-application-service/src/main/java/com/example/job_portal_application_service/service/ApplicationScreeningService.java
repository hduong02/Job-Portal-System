package com.example.job_portal_application_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.AiShortListStatus;
import com.example.dto.response.EducationResponse;
import com.example.dto.response.JobResponse;
import com.example.dto.response.JobSkillResponse;
import com.example.dto.response.ResumeResponse;
import com.example.dto.response.ResumeSkillResponse;
import com.example.dto.response.WorkExperienceResponse;
import com.example.job_portal_application_service.client.AiClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.model.ApplicationScreening;
import com.example.job_portal_application_service.payload.ScreeningScoreRequest;
import com.example.job_portal_application_service.payload.ScreeningScoreResponse;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.repository.ApplicationScreeningRepository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationScreeningService {

    private final JobClient jobClient;
    private final ResumeClient resumeClient;
    private final AiClient aiClient;
    private final ApplicationRepository applicationRepository;
    private final ApplicationScreeningRepository applicationScreeningRepository;

    @Async("screeningExecutor")
    @Transactional
    public void screenAsync(
            Long applicationId,
            Long candidateId,
            Long jobId,
            Long resumeId) {
        JobResponse job = jobClient.getJobById(jobId);

        ResumeResponse resume = resumeClient.getResumeById(resumeId, candidateId);

        List<String> requiredSkills = job.getSkills() != null
                ? job.getSkills().stream()
                        .map(JobSkillResponse::getName)
                        .collect(Collectors.toList()): Collections.emptyList();

        List<String> candidateSkills = resume.getSkills() != null
                ? resume.getSkills().stream()
                        .map(ResumeSkillResponse::getSkillName)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        List<String> candidateExperience = resume.getWorkExperiences() != null
                ? resume.getWorkExperiences().stream()
                        .map(ApplicationScreeningService::formatExperience)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        List<String> candidateEducation = resume.getEducations() != null
                ? resume.getEducations().stream()
                        .map(ApplicationScreeningService::formatEducation)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        ScreeningScoreRequest request = ScreeningScoreRequest.builder()
                .jobTitle(job.getTitle())
                .experienceLevel(job.getExperienceLevel() != null
                        ? job.getExperienceLevel().name()
                        : null)
                .requiredSkills(requiredSkills)
                .responsibilities(job.getResponsibilities())
                .candidateSummary(resume.getSummary())
                .candidateSkills(candidateSkills)
                .candidateExperience(candidateExperience)
                .candidateEducation(candidateEducation)
                .build();

        ScreeningScoreResponse result = aiClient.scoreCandidate(request);

        AiShortListStatus shortListStatus = resolveStatus(result.getScore());

        ApplicationScreening applicationScreening =
                ApplicationScreening.builder()
                        .applicationId(applicationId)
                        .overallScore(result.getScore())
                        .skillsMatchScore(result.getSkillsMatchScore())
                        .experienceMatchScore(result.getExperienceMatchScore())
                        .educationMatchScore(result.getEducationMatchScore())
                        .shortListStatus(shortListStatus)
                        .summary(result.getSummary())
                        .matchedSkills(result.getMatchedSkills())
                        .missingSkills(result.getMissingSkills())
                        .strengths(result.getStrengths())
                        .concerns(result.getConcerns())
                .build();

        applicationScreeningRepository.save(applicationScreening);

        applicationRepository.findById(applicationId).ifPresent(app -> {
            app.setAiScore(result.getScore());
            app.setAiShortListStatus(shortListStatus);
            applicationRepository.save(app);
        });

    }

    // Software engineer at Meta: handling spring boot project as team lead
    private static String formatExperience(WorkExperienceResponse w) {
        StringBuilder sb = new StringBuilder();
        if(w.getJobTitle() != null)
            sb.append(w.getJobTitle());
        if (w.getCompanyName() != null)
            sb.append(" at ").append(w.getCompanyName());
        if (w.getDescription() != null)
            sb.append(": ").append(w.getDescription());
        return sb.toString();
    }

    // Bachelor in computer science from MIT (3.6)
    private static String formatEducation(EducationResponse e) {
        StringBuilder sb = new StringBuilder();
        if (e.getDegree() != null)
            sb.append(e.getDegree());
        if (e.getFieldOfStudy() != null)
            sb.append(" in ").append(e.getFieldOfStudy());
        if (e.getInstitutionName() != null)
            sb.append(" from ").append(e.getInstitutionName());
        if (e.getGrade() != null) sb.append(" (").append(e.getGrade()).append(")");
        return sb.toString();
    }

    private static AiShortListStatus resolveStatus(int score) {
        if (score >= 90) return AiShortListStatus.AUTO_SHORTLISTED;
        if (score >= 75) return AiShortListStatus.REVIEW_RECOMMENDED;
        if (score >= 50) return AiShortListStatus.PENDING_REVIEW;
        return AiShortListStatus.LOW_MATCH;
    }

    public List<ApplicationScreening> getAll() {
        return applicationScreeningRepository.findAll();
    }
    
}
