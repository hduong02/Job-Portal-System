package com.example.job_portal_application_service.service;

import java.util.List;

import com.example.dto.response.ApplicationNoteResponse;
import com.example.job_portal_application_service.payload.AddApplicationNoteRequest;

public interface ApplicationNoteService {

    ApplicationNoteResponse addNote(
            Long applicationId,
            Long employerId,
            AddApplicationNoteRequest req) throws Exception;

    List<ApplicationNoteResponse> getNotesByApplication(
            Long applicationId, Long employerId
    );

    void deleteNote(
            Long applicationId,
            Long noteId,
            Long employerId) throws Exception;
}
