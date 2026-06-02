package com.example.job_portal_resume_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.response.WorkExperienceResponse;
import com.example.job_portal_resume_service.mapper.WorkExperienceMapper;
import com.example.job_portal_resume_service.model.Resume;
import com.example.job_portal_resume_service.model.WorkExperience;
import com.example.job_portal_resume_service.payload.AddWorkExperienceRequest;
import com.example.job_portal_resume_service.repository.WorkExperienceRepository;
import com.example.job_portal_resume_service.service.ResumeService;
import com.example.job_portal_resume_service.service.WorkExperienceService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkExperienceServiceImpl implements WorkExperienceService {
    private final ResumeService resumeService;
    private final WorkExperienceRepository workExperienceRepository;

    @Override
    public WorkExperienceResponse addWorkExperience(
            Long resumeId,
            Long candidateId,
            AddWorkExperienceRequest req) throws Exception {
        Resume resume = resumeService.getResumeEntity(resumeId);

        assertOwner(resume, candidateId);

        WorkExperience workExperience = WorkExperience.builder()
                .resume(resume)
                .companyName(req.getCompanyName())
                .companyLogoUrl(req.getCompanyLogoUrl())
                .jobTitle(req.getJobTitle())
                .employmentType(req.getEmploymentType())
                .location(req.getLocation())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .isCurrentJob(Boolean.TRUE.equals(req.getIsCurrentJob()))
                .description(req.getDescription())
                .technologies(req.getTechnologies() != null ? req.getTechnologies() : List.of())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .build();

        WorkExperience saved = workExperienceRepository.save(workExperience);
        return WorkExperienceMapper.toWorkExperienceResponse(saved);
    }



    @Override
    public List<WorkExperienceResponse> getWorkExperiences(Long resumeId) {
        return workExperienceRepository.findByResume_IdOrderByDisplayOrderAsc(resumeId)
                .stream().map(WorkExperienceMapper::toWorkExperienceResponse).toList();
    }

    @Override
    public WorkExperienceResponse updateWorkExperience(
            Long resumeId,
            Long candidateId,
            Long workExperienceId,
            AddWorkExperienceRequest req) throws Exception {
        WorkExperience exp = getWorkExperienceEntity(workExperienceId);
        assertOwner(exp.getResume(),candidateId);

        exp.setCompanyName(req.getCompanyName());
        exp.setCompanyLogoUrl(req.getCompanyLogoUrl());
        exp.setJobTitle(req.getJobTitle());
        exp.setEmploymentType(req.getEmploymentType());
        exp.setLocation(req.getLocation());
        exp.setStartDate(req.getStartDate());
        exp.setEndDate(req.getEndDate());
        exp.setIsCurrentJob(Boolean.TRUE.equals(req.getIsCurrentJob()));
        exp.setDescription(req.getDescription());
        if (req.getTechnologies() != null) exp.setTechnologies(req.getTechnologies());
        if (req.getDisplayOrder() != null) exp.setDisplayOrder(req.getDisplayOrder());

        WorkExperience saved=workExperienceRepository.save(exp);

        return WorkExperienceMapper.toWorkExperienceResponse(saved);
    }

    @Override
    public void deleteWorkExperience(
            Long resumeId,
            Long workExperienceId,
            Long candidateId) throws Exception {
        WorkExperience exp = getWorkExperienceEntity(workExperienceId);
        assertOwner(exp.getResume(),candidateId);
        workExperienceRepository.delete(exp);
    }

    @Override
    public WorkExperience getWorkExperienceEntity(Long workExperienceId) throws Exception {
        return workExperienceRepository.findById(workExperienceId).orElseThrow(
                () -> new Exception("work experience not found")
        );
    }

    private void assertOwner(Resume resume, Long candidateId) throws Exception {
        if(!resume.getCandidateId().equals(candidateId)) {
            throw new Exception("resume not found");
        }
    }
}