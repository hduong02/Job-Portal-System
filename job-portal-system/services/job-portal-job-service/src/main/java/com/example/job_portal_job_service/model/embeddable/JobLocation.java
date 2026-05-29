package com.example.job_portal_job_service.model.embeddable;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobLocation {

    private String address;
    private String city;
    private String country;
    private String state;
    private String zipCode;
}
