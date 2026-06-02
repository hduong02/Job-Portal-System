package com.example.job_portal_resume_service.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalInfo {

    private String firstName;
    private String lastName;

    private String headline;

    private String email;
    private String phone;
    private String city;
    private String country;

    private String profileImage;

    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private String websiteUrl;
}