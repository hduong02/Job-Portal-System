package com.example.job_portal_ai_service.payload;

import lombok.Data;

@Data
public class SalaryRangeResponse {

    private Long minSalary;
    private Long maxSalary;
    private String currency;
    private String period;
    private String marketInsight;
}
