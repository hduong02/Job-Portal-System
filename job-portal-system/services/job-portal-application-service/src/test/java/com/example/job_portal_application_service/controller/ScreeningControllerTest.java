package com.example.job_portal_application_service.controller;

import com.example.job_portal_application_service.service.ApplicationScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScreeningControllerTest {

    @Test
    void acknowledgesQueuedScreeningWithAcceptedStatus() {
        ApplicationScreeningService screenings = mock(ApplicationScreeningService.class);
        ScreeningController controller = new ScreeningController(screenings);

        var response = controller.createScreenings(1L, 2L, 3L, 4L);

        verify(screenings).screenAsync(1L, 2L, 3L, 4L);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isStatus());
        assertEquals("Screening queued", response.getBody().getMessage());
    }
}
