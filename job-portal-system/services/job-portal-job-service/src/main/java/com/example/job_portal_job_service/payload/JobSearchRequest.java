package com.example.job_portal_job_service.payload;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

import com.example.domain.ExperienceLevel;
import com.example.domain.JobStatus;
import com.example.domain.JobType;
import com.example.domain.WorkMode;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchRequest {

    private String keyword;

    private Long categoryId;

    private List<Long> skillIds;

    private List<Long> tagIds;

    private Long companyId;

    private String location;
    
    private BigDecimal minSalary;
    private BigDecimal maxSalary;

    private JobType jobType;

    private WorkMode workMode;

    private ExperienceLevel experienceLevel;
    
    private JobStatus status;

    private Integer minOpenings;
    private Integer maxOpenings;
}
