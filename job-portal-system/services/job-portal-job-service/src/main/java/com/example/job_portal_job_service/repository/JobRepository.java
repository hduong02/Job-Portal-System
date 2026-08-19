package com.example.job_portal_job_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.job_portal_job_service.model.Job;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    List<Job> findByCompanyId(Long companyId);

    Page<Job> findByCompanyId(Long companyId, Pageable pageable);
}
