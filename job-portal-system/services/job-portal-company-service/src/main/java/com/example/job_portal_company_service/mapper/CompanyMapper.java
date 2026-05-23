package com.example.job_portal_company_service.mapper;

import java.util.Collections;
import java.util.List;

import com.example.dto.response.CompanyResponse;
import com.example.dto.response.SocialLinkResponse;
import com.example.job_portal_company_service.model.Company;
import com.example.job_portal_company_service.model.SocialLink;

public class CompanyMapper {

    public static CompanyResponse toResponse(Company company) {
        List<SocialLinkResponse> socialLinks = company.getSocialLinks() == null ?
                Collections.emptyList() : company.getSocialLinks().stream()
                        .map(CompanyMapper::toSocialLinkResponse)
                        .toList();

        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .slug(company.getSlug())
                .tagline(company.getTagline())
                .description(company.getDescription())
                .logoUrl(company.getLogoUrl())
                .coverImageUrl(company.getCoverImageUrl())
                .website(company.getWebsite())
                .email(company.getEmail())
                .phone(company.getPhone())
                .foundedYear(company.getFoundedYear())
                .companySize(company.getCompanySize())
                .companyType(company.getCompanyType())
                .industryType(company.getIndustryType())
                .status(company.getStatus())
                .active(company.getActive())
                .ownerId(company.getOwnerId())
                .socialLinks(socialLinks)
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }

    public static SocialLinkResponse toSocialLinkResponse(SocialLink socialLink) {
        return SocialLinkResponse.builder()
                .platform(socialLink.getPlatform())
                .url(socialLink.getUrl())
                .build();
    }
}