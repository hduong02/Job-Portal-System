package com.example.job_portal_application_service.event;
import com.example.dto.response.JobResponse;
import com.example.dto.response.CompanyResponse;
import com.example.job_portal_application_service.client.JobClient;
import com.example.job_portal_application_service.client.CompanyClient;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationDetailsCacheTest {
    private final JobClient jobs = mock(JobClient.class);
    private final CompanyClient companies = mock(CompanyClient.class);
    private final AtomicLong clock = new AtomicLong();
    private final NotificationDetailsCache cache = new NotificationDetailsCache(
            jobs, companies, Duration.ofSeconds(30), 100, 10, clock::get);
    @Test
    void reusesDisplayDetailsAndRefreshesAfterExpiry() {
        when(jobs.getJobById(1L)).thenReturn(JobResponse.builder().title("Original").build(),
                JobResponse.builder().title("Renamed").build());
        when(companies.getCompanyById(2L)).thenReturn(CompanyResponse.builder().name("Company").build());
        assertEquals("Original", cache.jobTitle(1L));
        assertEquals("Original", cache.jobTitle(1L));
        assertEquals("Company", cache.companyName(2L));
        assertEquals("Company", cache.companyName(2L));
        verify(jobs, times(1)).getJobById(1L);
        verify(companies, times(1)).getCompanyById(2L);
        clock.set(Duration.ofSeconds(31).toNanos());
        assertEquals("Renamed", cache.jobTitle(1L));
        verify(jobs, times(2)).getJobById(1L);
    }
    @Test
    void failedLookupIsNotCached() {
        when(jobs.getJobById(1L)).thenThrow(new IllegalStateException("unavailable"))
                .thenReturn(JobResponse.builder().title("Recovered").build());
        assertThrows(IllegalStateException.class, () -> cache.jobTitle(1L));
        assertEquals("Recovered", cache.jobTitle(1L));
    }
    @Test
    void concurrentColdRequestsShareOneLookup() throws Exception {
        var loading = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(jobs.getJobById(1L)).thenAnswer(invocation -> {
            loading.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            return JobResponse.builder().title("Job").build();
        });
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> cache.jobTitle(1L))).toList();
            try { assertTrue(loading.await(5, TimeUnit.SECONDS)); } finally { release.countDown(); }
            for (var task : tasks) assertEquals("Job", task.get(5, TimeUnit.SECONDS));
        }
        verify(jobs, times(1)).getJobById(1L);
    }
}
