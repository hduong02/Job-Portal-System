package com.example.job_portal_application_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.domain.ApplicationStatus;
import com.example.dto.response.ApplicationResponse;
import com.example.dto.response.CompanyResponse;
import com.example.dto.response.JobResponse;
import com.example.dto.response.ResumeResponse;
import com.example.dto.response.UserResponse;
import com.example.job_portal_application_service.client.CompanyClient;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.ResumeClient;
import com.example.job_portal_application_service.client.UserClient;
import com.example.job_portal_application_service.mapper.ApplicationMapper;
import com.example.job_portal_application_service.model.Application;
import com.example.job_portal_application_service.model.ApplicationNote;
import com.example.job_portal_application_service.payload.CompanyApplicationFilterRequest;
import com.example.job_portal_application_service.payload.CreateApplicationRequest;
import com.example.job_portal_application_service.payload.WithdrawApplicationRequest;
import com.example.job_portal_application_service.repository.ApplicationNoteRepository;
import com.example.job_portal_application_service.repository.ApplicationRepository;
import com.example.job_portal_application_service.repository.ApplicationSpecification;
import com.example.job_portal_application_service.service.ApplicationService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationNoteRepository applicationNoteRepository;
    private final JobClient jobClient;
    private final ResumeClient resumeClient;
    private final CompanyClient companyClient;
    private final UserClient userClient;

    @Override
    public ApplicationResponse createApplication(Long candidateId,
            CreateApplicationRequest req) throws Exception {

        if (applicationRepository.existsByCandidateIdAndJobId(
                candidateId, req.getJobId())) {
            throw new Exception("You have already applied");
        }

        JobResponse job = jobClient.getJobById(req.getJobId());
        Long companyId = job.getCompany().getId();
        Long employerId = job.getEmployerId();

        ResumeResponse resume = resumeClient.getResumeById(
                req.getResumeId(),
                candidateId
        );

        Application application = ApplicationMapper.toEntity(
                req,
                candidateId,
                companyId,
                employerId
        );

        Application savedApplication = applicationRepository.save(application);

        return buildFullResponse(savedApplication);
    }

    @Override
    public ApplicationResponse getApplicationById(Long id) throws Exception {
        Application application = getApplicationEntity(id);
        return buildFullResponse(application);
    }

    @Override
    public List<ApplicationResponse> getMyApplications(Long candidateId) {
        return applicationRepository.findByCandidateId(candidateId)
                .stream()
                .map(this::buildFullResponse)
                .toList();
    }

    @Override
    public List<ApplicationResponse> getApplicationsForJob(Long jobId) {
        return applicationRepository.findByJobId(jobId)
                .stream()
                .map(this::buildFullResponse)
                .toList();
    }

    @Override
    public List<ApplicationResponse> getApplicationsForCompany(Long userId,
            CompanyApplicationFilterRequest filter) {

        Long companyId = companyClient.getMyCompany(userId).getId();
        Sort sort = buildSort(filter.getSortBy());

        return applicationRepository.findAll(
                ApplicationSpecification.forCompanyWithFilters(
                    companyId,
                    filter.getJobId(),
                    filter.getStatus(),
                    filter.getIsStarred(),
                    filter.getAiShortListStatus(),
                    filter.getMinAiScore()
                ), sort)
                .stream()
                .map(this::buildFullResponse)
                .toList();
    }

    @Override
    public ApplicationResponse updateStatus(
            Long applicationId,
            Long employerId,
            ApplicationStatus status) throws Exception {

        Application application = getApplicationEntity(applicationId);
        assertEmployer(application, employerId);

        if (application.getStatus() == ApplicationStatus.WITHDRAWN){
            throw new Exception("Candidate have already withdrawn");
        }
        application.setStatus(status);
        Application savedApplication = applicationRepository.save(application);

        return buildFullResponse(savedApplication);
    }

    @Override
    public ApplicationResponse withdraw(
            Long applicationId,
            Long candidateId,
            WithdrawApplicationRequest req) throws Exception {
            
        Application application = getApplicationEntity(applicationId);
        assertCandidate(application,candidateId);
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setWithdrawnReason(req.getReason());
        Application savedApplication = applicationRepository.save(application);
        return buildFullResponse(savedApplication);
    }

    @Override
    public ApplicationResponse toggleStar(Long applicationId, Long employerId)
            throws Exception {
        Application application = getApplicationEntity(applicationId);
        assertEmployer(application, employerId);

        if (application.getIsStarred() == null){
            application.setIsStarred(true);
        }

        application.setIsStarred(!application.getIsStarred());
        Application savedApplication = applicationRepository.save(application);
        return buildFullResponse(savedApplication);
    }

    @Override
    public void deleteApplication(Long applicationId, Long candidateId) throws Exception {
        Application application = getApplicationEntity(applicationId);
        assertCandidate(application,candidateId);
        applicationRepository.delete(application);
    }

    @Override
    public Application getApplicationEntity(Long id) throws Exception {
        return applicationRepository.findById(id).orElseThrow(
                () -> new Exception("Application not found")
        );
    }

    // ====================== Helper functions ======================

    private ApplicationResponse buildFullResponse(Application application) {
        JobResponse job = jobClient.getJobById(application.getJobId());
        CompanyResponse company = companyClient.getCompanyById(application.getCompanyId());
        UserResponse candidate = userClient.getUserById(application.getCandidateId());

        List<ApplicationNote> notes = applicationNoteRepository
                .findByApplicationId(application.getId());

        return ApplicationMapper.toResponse(
                application,
                notes,
                job,
                company,
                candidate
        );
    }
    
    private void assertCandidate(Application application, Long candidateId) throws Exception {
        if (!application.getCandidateId().equals(candidateId)) {
            throw new Exception("You are not the owner of this application");
        }
    }

    private void assertEmployer(Application application, Long employerId) throws Exception {
        if (!application.getEmployerId().equals(employerId)) {
            throw new Exception("You are not the employer for this application");
        }
    }

    private Sort buildSort(String sortBy) {
        if ("AI_SCORE_DESC".equals(sortBy)) {
            return Sort.by(Sort.Order.desc("aiScore").with(Sort.NullHandling.NULLS_LAST));
        }
        else if ("AI_SCORE_ASC".equals(sortBy)) {
            return Sort.by(Sort.Order.asc("aiScore").with(Sort.NullHandling.NULLS_LAST));
        }
        return Sort.by(Sort.Direction.DESC, "appliedAt");
    }
}
