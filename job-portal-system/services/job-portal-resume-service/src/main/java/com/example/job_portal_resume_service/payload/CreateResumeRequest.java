package com.example.job_portal_resume_service.payload;

import com.example.domain.ResumeTemplate;
import com.example.domain.ResumeVisibility;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateResumeRequest {

    @NotBlank(message = "Resume title is required")
    private String title;

    private ResumeTemplate template;

    private ResumeVisibility visibility;

    private Boolean isDefault;
}
