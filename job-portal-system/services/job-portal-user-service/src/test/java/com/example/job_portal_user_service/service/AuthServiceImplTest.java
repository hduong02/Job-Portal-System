package com.example.job_portal_user_service.service;

import com.example.domain.UserRole;
import com.example.domain.UserStatus;
import com.example.job_portal_user_service.model.User;
import com.example.job_portal_user_service.payload.LoginRequest;
import com.example.job_portal_user_service.repository.UserRepository;
import com.example.job_portal_user_service.security.CustomUserDetailsService;
import com.example.job_portal_user_service.security.JwtProvider;
import com.example.job_portal_user_service.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtProvider jwt = mock(JwtProvider.class);
    private final CustomUserDetailsService details = mock(CustomUserDetailsService.class);
    private final AuthServiceImpl service = new AuthServiceImpl(users, passwords, jwt, details);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginRejectsNonActiveAccountsBeforeIssuingToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("candidate@example.com");
        request.setPassword("password");
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(request.getEmail()).password("encoded").roles("USER").build();
        when(details.loadUserByUsername(request.getEmail())).thenReturn(userDetails);
        when(passwords.matches(request.getPassword(), "encoded")).thenReturn(true);

        for (UserStatus status : new UserStatus[]{UserStatus.SUSPENDED,
                UserStatus.INACTIVE, UserStatus.DELETED}) {
            when(users.findByEmail(request.getEmail())).thenReturn(User.builder()
                    .id(1L).email(request.getEmail()).status(status)
                    .role(UserRole.ROLE_JOB_SEEKER).build());

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.login(request));
            assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
        verifyNoInteractions(jwt);
        verify(users, never()).save(any());
    }

    @Test
    void activeAccountCanLogIn() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("candidate@example.com");
        request.setPassword("password");
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(request.getEmail()).password("encoded").roles("USER").build();
        User user = User.builder().id(1L).fullName("Candidate")
                .email(request.getEmail()).status(UserStatus.ACTIVE)
                .role(UserRole.ROLE_JOB_SEEKER).build();
        when(details.loadUserByUsername(request.getEmail())).thenReturn(userDetails);
        when(passwords.matches(request.getPassword(), "encoded")).thenReturn(true);
        when(users.findByEmail(request.getEmail())).thenReturn(user);
        when(jwt.generateToken(any(), eq(1L))).thenReturn("token");

        assertEquals("token", service.login(request).getJwt());
        verify(users).save(user);
    }
}
