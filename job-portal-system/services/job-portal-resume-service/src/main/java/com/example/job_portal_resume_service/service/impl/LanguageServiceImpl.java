package com.example.job_portal_resume_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.dto.response.LanguageResponse;
import com.example.job_portal_resume_service.mapper.ResumeMapper;
import com.example.job_portal_resume_service.model.Language;
import com.example.job_portal_resume_service.model.Resume;
import com.example.job_portal_resume_service.payload.AddLanguageRequest;
import com.example.job_portal_resume_service.repository.LanguageRepository;
import com.example.job_portal_resume_service.service.LanguageService;
import com.example.job_portal_resume_service.service.ResumeService;


import java.util.List;

@Service
@RequiredArgsConstructor
public class LanguageServiceImpl implements LanguageService {

    private final ResumeService resumeService;
    private final LanguageRepository languageRepository;

    @Override
    public LanguageResponse addLanguage(
            Long resumeId,
            Long candidateId,
            AddLanguageRequest req) throws Exception {
        Resume resume = resumeService.getResumeEntity(resumeId);
        assertOwner(resume, candidateId);

        Language lang = Language.builder()
                .resume(resume)
                .languageName(req.getLanguageName())
                .proficiency(req.getProficiency())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .build();

        Language saved = languageRepository.save(lang);

        return ResumeMapper.toLanguageResponse(saved);
    }

    @Override
    public List<LanguageResponse> getLanguages(Long resumeId) {
        return languageRepository.findByResume_IdOrderByDisplayOrderAsc(resumeId)
                .stream().map(ResumeMapper::toLanguageResponse).toList();
    }

    @Override
    public LanguageResponse updateLanguage(
            Long languageId,
            Long resumeId,
            Long candidateId,
            AddLanguageRequest req) throws Exception {
        Language lang = languageRepository.findById(languageId).orElseThrow(
                () -> new Exception("Language not found")
        );
        assertOwner(lang.getResume(), candidateId);
        lang.setLanguageName(req.getLanguageName());
        lang.setProficiency(req.getProficiency());
        if (req.getDisplayOrder() != null) lang.setDisplayOrder(req.getDisplayOrder());


        return ResumeMapper.toLanguageResponse(languageRepository.save(lang));
    }

    @Override
    public void deleteLanguage(
            Long languageId,
            Long resumeId,
            Long candidateId) throws Exception {
        Language lang = languageRepository.findById(languageId).orElseThrow(
                () -> new Exception("Language not found")
        );
        assertOwner(lang.getResume(), candidateId);
        languageRepository.delete(lang);
    }

    private void assertOwner(Resume resume, Long candidateId) throws Exception {
        if (!resume.getCandidateId().equals(candidateId)) {
            throw new Exception("Resume not found");
        }
    }
}