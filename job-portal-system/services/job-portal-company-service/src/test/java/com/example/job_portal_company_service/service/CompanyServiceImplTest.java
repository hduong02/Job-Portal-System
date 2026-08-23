package com.example.job_portal_company_service.service;

import com.example.dto.request.CompanyRequest;
import com.example.job_portal_company_service.model.Company;
import com.example.job_portal_company_service.repository.CompanyRepository;
import com.example.job_portal_company_service.service.impl.CompanyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CompanyServiceImplTest {

    @Test
    void updateRejectsSomeoneOtherThanTheOwner() {
        CompanyRepository repository = mock(CompanyRepository.class);
        CompanyServiceImpl service = new CompanyServiceImpl(repository);
        when(repository.findById(10L)).thenReturn(Optional.of(
                Company.builder().id(10L).ownerId(20L).build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateCompany(10L, 30L, new CompanyRequest()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(repository, never()).save(any());
    }
}
