package com.example.job_portal_application_service.payload;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApplicationRequest {

    @NotNull(message = "Job ID is required")
    private Long jobId;

    @NotNull(message = "Resume ID is required")
    private Long resumeId;

    @Size(max = 3000, message = "Cover letter must not exceed 3000 characters")
    private String coverLetter;

    @DecimalMin(value = "0.0", message = "Expected salary must not be negative")
    private BigDecimal expectedSalary;

    private LocalDate availableFrom;
}
