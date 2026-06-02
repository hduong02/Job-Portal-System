package com.example.job_portal_resume_service.service;

import java.util.List;

import com.example.dto.response.LanguageResponse;
import com.example.job_portal_resume_service.payload.AddLanguageRequest;

public interface LanguageService {

    LanguageResponse addLanguage(
            Long resumeId,
            Long candidateId,
            AddLanguageRequest req) throws Exception;

    List<LanguageResponse> getLanguages(Long resumeId);

    LanguageResponse updateLanguage(
            Long languageId,
            Long resumeId,
            Long candidateId,
            AddLanguageRequest req) throws Exception;

    void deleteLanguage(Long languageId, Long resumeId, Long candidateId) throws Exception;

}
