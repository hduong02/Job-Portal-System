package com.example.job_portal_job_service.service;

import com.example.dto.response.CompanyResponse;
import com.example.dto.response.JobResponse;
import com.example.job_portal_job_service.client.CompanyClient;
import com.example.job_portal_job_service.model.Job;
import com.example.job_portal_job_service.model.JobCategory;
import com.example.job_portal_job_service.repository.JobRepository;
import com.example.job_portal_job_service.service.impl.JobServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class JobServiceImplTest {

    @Test
    void responseLoadsCompanyByCompanyId() throws Exception {
        JobRepository jobs = mock(JobRepository.class);
        CompanyClient companies = mock(CompanyClient.class);
        JobServiceImpl service = new JobServiceImpl(jobs,
                mock(JobCategoryService.class), mock(JobSkillService.class),
                mock(JobTagService.class), companies);
        Job job = Job.builder().id(1L).employerId(20L).companyId(30L)
                .category(JobCategory.builder().id(40L).build()).build();
        CompanyResponse company = CompanyResponse.builder().id(30L).build();
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(companies.getCompanyById(30L)).thenReturn(company);

        JobResponse response = service.getJobById(1L);

        assertSame(company, response.getCompany());
        verify(companies).getCompanyById(30L);
        verify(companies, never()).getCompanyById(20L);
    }
}
