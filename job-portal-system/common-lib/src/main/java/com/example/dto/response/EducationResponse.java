package com.example.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EducationResponse {
    private Long id;
    private String institutionName;
    private String degree;
    private String fieldOfStudy;
    private String grade;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isCurrentlyStudying;
    private String description;
    private Integer displayOrder;
}