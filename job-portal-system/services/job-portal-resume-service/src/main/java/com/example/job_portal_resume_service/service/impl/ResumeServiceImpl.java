package com.example.job_portal_resume_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.dto.response.EducationResponse;
import com.example.dto.response.LanguageResponse;
import com.example.dto.response.PersonalInfoResponse;
import com.example.dto.response.ProjectResponse;
import com.example.dto.response.ResumeResponse;
import com.example.dto.response.ResumeSkillResponse;
import com.example.dto.response.WorkExperienceResponse;
import com.example.job_portal_resume_service.mapper.ResumeMapper;
import com.example.job_portal_resume_service.mapper.WorkExperienceMapper;
import com.example.job_portal_resume_service.model.PersonalInfo;
import com.example.job_portal_resume_service.model.Resume;
import com.example.job_portal_resume_service.payload.CreateResumeRequest;
import com.example.job_portal_resume_service.repository.EducationRepository;
import com.example.job_portal_resume_service.repository.LanguageRepository;
import com.example.job_portal_resume_service.repository.ProjectRepository;
import com.example.job_portal_resume_service.repository.ResumeRepository;
import com.example.job_portal_resume_service.repository.ResumeSkillRepository;
import com.example.job_portal_resume_service.repository.WorkExperienceRepository;
import com.example.job_portal_resume_service.service.ResumeService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ResumeServiceImpl implements ResumeService {
    private final ResumeRepository resumeRepository;
    private final WorkExperienceRepository workExperienceRepository;
    private final EducationRepository educationRepository;
    private final ResumeSkillRepository resumeSkillRepository;
    private final ProjectRepository projectRepository;
    private final LanguageRepository languageRepository;

    @Override
    public ResumeResponse createResume(Long candidateId, CreateResumeRequest req) {

        if (Boolean.TRUE.equals(req.getIsDefault())) {
            resumeRepository.findByCandidateIdAndIsDefaultTrue(candidateId)
                    .ifPresent(existing -> {
                        existing.setIsDefault(false);
                        resumeRepository.save(existing);
                    });
        }

        Resume resume = Resume.builder()
                .candidateId(candidateId)
                .title(req.getTitle())
                .template(req.getTemplate())
                .visibility(req.getVisibility())
                .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
                .isActive(true)
                .build();
        Resume saved = resumeRepository.save(resume);
        return buildFullResponse(saved);
    }

    @Override
    public ResumeResponse getResumeById(Long resumeId, Long candidateId)
            throws Exception {
        Resume resume = getResumeEntity(resumeId);
        assertOwner(resume, candidateId);
        return buildFullResponse(resume);
    }


    @Override
    public Page<ResumeResponse> getMyResumes(Long candidateId, Pageable pageable) {
        return resumeRepository.findByCandidateIdAndIsActiveTrue(candidateId, pageable)
                .map(this::buildFullResponse);
    }

    @Override
    public ResumeResponse updatePersonalInfo(
            Long resumeId,
            Long candidateId,
            PersonalInfoResponse req
    ) throws Exception {

        Resume resume = getResumeEntity(resumeId);

        assertOwner(resume, candidateId);

        PersonalInfo info = resume.getPersonalInfo();
        if (info == null) info = new PersonalInfo();

        if(req.getFirstName() != null)
            info.setFirstName(req.getFirstName());
        if(req.getLastName() != null)
            info.setLastName(req.getLastName());
        if (req.getHeadline() != null) info.setHeadline(req.getHeadline());
        if (req.getEmail() != null) info.setEmail(req.getEmail());
        if (req.getPhone() != null) info.setPhone(req.getPhone());
        if (req.getCity() != null) info.setCity(req.getCity());
        if (req.getCountry() != null) info.setCountry(req.getCountry());
        if (req.getLinkedinUrl() != null) info.setLinkedinUrl(req.getLinkedinUrl());
        if (req.getGithubUrl() != null) info.setGithubUrl(req.getGithubUrl());
        if (req.getPortfolioUrl() != null) info.setPortfolioUrl(req.getPortfolioUrl());
        if (req.getWebsiteUrl() != null) info.setWebsiteUrl(req.getWebsiteUrl());
        if(req.getProfileImage() != null) info.setProfileImage(req.getProfileImage());

        resume.setPersonalInfo(info);
        Resume updated = resumeRepository.save(resume);

        return buildFullResponse(updated);
    }

    @Override
    public ResumeResponse updateSummary(
            Long resumeId,
            Long candidateId,
            String summary
    ) throws Exception {
        Resume resume = getResumeEntity(resumeId);

        assertOwner(resume, candidateId);

        resume.setSummary(summary);
        Resume updated = resumeRepository.save(resume);

        return buildFullResponse(updated);
    }

    @Override
    public ResumeResponse setDefaultResume(Long resumeId, Long candidateId)
            throws Exception {
        Resume resume = getResumeEntity(resumeId);
        assertOwner(resume, candidateId);

        resumeRepository.findByCandidateIdAndIsDefaultTrue(candidateId)
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    resumeRepository.save(existing);
                });

        resume.setIsDefault(true);
        Resume updated = resumeRepository.save(resume);
        return buildFullResponse(updated);
    }

    @Override
    public void deleteResume(Long resumeId, Long candidateId) throws Exception {
        Resume resume = getResumeEntity(resumeId);
        assertOwner(resume, candidateId);
        resume.setIsActive(false);
        resume.setIsDefault(false);
        resumeRepository.save(resume);
    }

    @Override
    public Resume getResumeEntity(Long resumeId) throws Exception {
        return resumeRepository.findById(resumeId).orElseThrow(
                () -> new Exception("resume not found with id "+resumeId)
        );
    }

    private ResumeResponse buildFullResponse(Resume resume) {

        Long resumeId = resume.getId();

        List<WorkExperienceResponse> workExperienceResponses =
                workExperienceRepository.findByResume_IdOrderByDisplayOrderAsc(resumeId)
                        .stream()
                        .map(WorkExperienceMapper::toWorkExperienceResponse)
                        .toList();
        List<EducationResponse> educationResponses =
                educationRepository.findByResume_IdOrderByDisplayOrderAsc(resumeId)
                        .stream()
                        .map(ResumeMapper::toEducationResponse).toList();
        List<ResumeSkillResponse> skills = resumeSkillRepository
                .findByResume_IdOrderByDisplayOrderAsc(resumeId)
                .stream()
                .map(ResumeMapper::toSkillResponse).toList();

        List<ProjectResponse> projects = projectRepository
                .findByResume_IdOrderByDisplayOrderAsc(resumeId)
                .stream()
                .map(ResumeMapper::toProjectResponse).toList();

        List<LanguageResponse> languageResponses =
                languageRepository.findByResume_IdOrderByDisplayOrderAsc(resumeId)
                        .stream().map(ResumeMapper::toLanguageResponse).toList();

        return ResumeMapper.toResponse(
                resume,
                workExperienceResponses,
                educationResponses,
                skills,
                projects,
                languageResponses
        );
    }

    private void assertOwner(Resume resume, Long candidateId) throws Exception {
        if (!resume.getCandidateId().equals(candidateId)) {
            throw new Exception("resume not found with id");
        }
    }
}
