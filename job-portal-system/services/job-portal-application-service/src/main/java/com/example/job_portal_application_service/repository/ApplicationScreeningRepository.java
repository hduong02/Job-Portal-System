package com.example.job_portal_application_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_application_service.model.ApplicationScreening;

public interface ApplicationScreeningRepository extends
        JpaRepository<ApplicationScreening, Long> {

    ApplicationScreening findByApplicationId(Long applicationId);

}
