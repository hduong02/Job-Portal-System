package com.example.job_portal_resume_service.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddEducationRequest {

    @NotBlank(message = "institution name is required")
    private String institutionName;

    @NotBlank(message = "Degree is required")
    private String degree;

    private String fieldOfStudy;
    private String grade;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isCurrentlyStudying = false;

    private String description;
    private Integer displayOrder;
}
