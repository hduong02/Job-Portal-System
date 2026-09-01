package com.example.job_portal_application_service.event;

import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Short-lived display data only; never used for authorization or job eligibility. */
@Component
public class NotificationDetailsCache {
    private final JobClient jobs;
    private final CompanyClient companies;
    private final Cache<Long, String> jobTitles;
    private final Cache<Long, String> companyNames;

    @Autowired
    public NotificationDetailsCache(JobClient jobs, CompanyClient companies,
            @Value("${application.notification-cache.ttl-seconds:30}") long ttlSeconds,
            @Value("${application.notification-cache.max-jobs:2000}") long maxJobs,
            @Value("${application.notification-cache.max-companies:500}") long maxCompanies) {
        this(jobs, companies, Duration.ofSeconds(ttlSeconds), maxJobs, maxCompanies, Ticker.systemTicker());
    }

    NotificationDetailsCache(JobClient jobs, CompanyClient companies, Duration ttl,
            long maxJobs, long maxCompanies, Ticker ticker) {
        this.jobs = jobs;
        this.companies = companies;
        this.jobTitles = Caffeine.newBuilder().maximumSize(maxJobs).expireAfterWrite(ttl).ticker(ticker).build();
        this.companyNames = Caffeine.newBuilder().maximumSize(maxCompanies).expireAfterWrite(ttl).ticker(ticker).build();
    }

    public String jobTitle(Long id) {
        return jobTitles.get(id, key -> jobs.getJobById(key).getTitle());
    }

    public String companyName(Long id) {
        return companyNames.get(id, key -> companies.getCompanyById(key).getName());
    }
}
