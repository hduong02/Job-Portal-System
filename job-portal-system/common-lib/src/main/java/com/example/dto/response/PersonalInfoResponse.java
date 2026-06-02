package com.example.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalInfoResponse {

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
