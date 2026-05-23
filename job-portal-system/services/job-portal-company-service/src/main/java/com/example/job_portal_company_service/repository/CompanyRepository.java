package com.example.job_portal_company_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.domain.CompanyStatus;
import com.example.domain.CompanyType;
import com.example.domain.IndustryType;
import com.example.job_portal_company_service.model.Company;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company,Long> {

    Optional<Company> findByOwnerId(Long ownerId);
    boolean existsByOwnerId(Long ownerId);
    boolean existsByName(String name);
    boolean existsBySlug(String slug);
    boolean existsByRegistrationNumber(String registrationNumber);

    @Query(
            "select c from Company c where " +
            "(:companyType Is NULL OR c.companyType=:companyType) AND "+
            "(:industryType IS NULL OR c.industryType = :industryType) AND " +
            "(:status IS NULL OR c.status = :status)"
    )
    List<Company> findByFilters(
            @Param("companyType") CompanyType companyType,
            @Param("industryType") IndustryType industryType,
            @Param("status") CompanyStatus status
    );
}