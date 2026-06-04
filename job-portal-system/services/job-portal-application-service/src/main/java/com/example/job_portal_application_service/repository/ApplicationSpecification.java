package com.example.job_portal_application_service.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import com.example.domain.AiShortListStatus;
import com.example.domain.ApplicationStatus;
import com.example.job_portal_application_service.model.Application;

import java.util.ArrayList;
import java.util.List;

public class ApplicationSpecification {

    public static Specification<Application> forCompanyWithFilters(
            Long companyId,
            Long jobId,
            ApplicationStatus status,
            boolean isStarred,
            AiShortListStatus aiShortListStatus,
            Integer minAiScore
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (jobId != null) predicates.add(cb.equal(root.get("jobId"), jobId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (isStarred) predicates.add(cb.equal(root.get("isStarred"), isStarred));
            if (aiShortListStatus != null) predicates.add(cb.equal(
                    root.get("aiShortListStatus"), aiShortListStatus));
            if (minAiScore != null) predicates.add(cb.greaterThanOrEqualTo(
                    root.get("aiScore"), minAiScore));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
