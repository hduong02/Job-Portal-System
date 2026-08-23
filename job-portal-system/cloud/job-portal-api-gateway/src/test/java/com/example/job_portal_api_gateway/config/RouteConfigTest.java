package com.example.job_portal_api_gateway.config;

import com.example.job_portal_api_gateway.jwt.JwtConstant;
import com.example.job_portal_api_gateway.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RouteConfigTest {

    @Test
    void nonAdminCannotVerifyCompany() {
        JwtUtil jwt = mock(JwtUtil.class);
        RouteConfig routes = new RouteConfig(jwt);
        ServerRequest request = mock(ServerRequest.class);
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(headers.firstHeader(JwtConstant.JWT_HEADER))
                .thenReturn(JwtConstant.TOKEN_PREFIX + "token");
        when(request.method()).thenReturn(HttpMethod.PATCH);
        when(request.path()).thenReturn("/api/companies/1/verify");
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extractAuthorities("token")).thenReturn("ROLE_EMPLOYER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> routes.jwtAuthFilter(request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }
}
