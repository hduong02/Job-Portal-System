package com.example.job_portal_application_service.mapper;

import java.util.List;

import com.example.domain.ApplicationStatus;
import com.example.dto.response.ApplicationNoteResponse;
import com.example.dto.response.ApplicationResponse;
import com.example.dto.response.CompanyResponse;
import com.example.dto.response.JobResponse;
import com.example.dto.response.UserResponse;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.model.ApplicationNote;
import com.example.job_portal_application_service.payload.CreateApplicationRequest;

public class ApplicationMapper {

    public static Application toEntity(
            CreateApplicationRequest req,
            Long candidateId,
            Long companyId,
            Long employerId) {
        if (req == null) return null;

        return Application.builder()
                .candidateId(candidateId)
                .jobId(req.getJobId())
                .companyId(companyId)
                .employerId(employerId)
                .resumeId(req.getResumeId())
                .coverLetter(req.getCoverLetter())
                .expectedSalary(req.getExpectedSalary())
                .availableFrom(req.getAvailableFrom())
                .status(ApplicationStatus.PENDING)
                .isStarred(false)
                .build();
    }

    public static ApplicationResponse toResponse(
            Application application,
            List<ApplicationNote> notes,
            JobResponse job,
            CompanyResponse company,
            UserResponse candidate) {

        return ApplicationResponse.builder()
                .id(application.getId())
                .candidate(candidate)
                .employerId(application.getEmployerId())
                .job(job)
                .company(company)
                .status(application.getStatus())

                .resumeId(application.getResumeId())
                .coverLetter(application.getCoverLetter())

                .expectedSalary(application.getExpectedSalary())

                .availableFrom(application.getAvailableFrom())

                .isStarred(application.getIsStarred())
                .notes(notes.stream().map(ApplicationMapper::toNoteResponse).toList())
                .withdrawnAt(application.getWithdrawnAt())
                .withdrawnReason(application.getWithdrawnReason())
                .appliedAt(application.getAppliedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    public static ApplicationNoteResponse toNoteResponse(ApplicationNote note) {
        return ApplicationNoteResponse.builder()
                .id(note.getId())
                .addedByUserId(note.getAddedByUserId())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .build();
    }
}

