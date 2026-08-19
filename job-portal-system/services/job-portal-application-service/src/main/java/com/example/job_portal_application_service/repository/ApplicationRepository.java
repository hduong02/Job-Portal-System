package com.example.job_portal_application_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.job_portal_application_service.model.Application;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long>,
        JpaSpecificationExecutor<Application> {

    List<Application> findByCandidateId(Long candidateId);

    Page<Application> findByCandidateId(Long candidateId, Pageable pageable);

    List<Application> findByJobId(Long jobId);

    Page<Application> findByJobId(Long jobId, Pageable pageable);

    boolean existsByCandidateIdAndJobId(Long candidateId, Long jobId);
}
