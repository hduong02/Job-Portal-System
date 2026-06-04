package com.example.job_portal_application_service.payload;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawApplicationRequest {

    private String reason;
}

