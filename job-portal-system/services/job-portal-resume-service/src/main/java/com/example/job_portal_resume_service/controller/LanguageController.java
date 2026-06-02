package com.example.job_portal_resume_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.ApiResponse;
import com.example.dto.response.LanguageResponse;
import com.example.job_portal_resume_service.payload.AddLanguageRequest;
import com.example.job_portal_resume_service.service.LanguageService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resumes/{resumeId}/languages")
public class LanguageController {

    private final LanguageService languageService;

    @PostMapping
    public ResponseEntity<LanguageResponse> addLanguage(
            @PathVariable Long resumeId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddLanguageRequest addLanguageRequest) throws Exception {
        return ResponseEntity.ok(
                languageService.addLanguage(resumeId, candidateId, addLanguageRequest)
        );
    }

    @GetMapping
    public ResponseEntity<List<LanguageResponse>> getLanguages(
            @PathVariable Long resumeId) throws Exception {
        return ResponseEntity.ok(languageService.getLanguages(resumeId));
    }

    @PutMapping("/{languageId}")
    public ResponseEntity<LanguageResponse> updateLanguage(
            @PathVariable Long resumeId,
            @PathVariable Long languageId,
            @RequestHeader("X-User-Id") Long candidateId,
            @RequestBody @Valid AddLanguageRequest req) throws Exception {
        return ResponseEntity.ok(
                languageService.updateLanguage(languageId, resumeId, candidateId, req));
    }

    @DeleteMapping("/{languageId}")
    public ResponseEntity<ApiResponse> deleteLanguage(
            @PathVariable Long resumeId,
            @PathVariable Long languageId,
            @RequestHeader("X-User-Id") Long candidateId) throws Exception {
        languageService.deleteLanguage(languageId, resumeId, candidateId);
        return ResponseEntity.ok(new ApiResponse("Language deleted successfully", true));
    }
}
