package com.example.job_portal_company_service.model;

import com.example.domain.SocialPlatform;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialLink {

    private SocialPlatform platform;
    private String url;
}
