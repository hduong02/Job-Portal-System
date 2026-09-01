package com.example.job_portal_api_gateway.config;

import com.example.job_portal_api_gateway.jwt.JwtConstant;
import com.example.job_portal_api_gateway.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

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
        Claims claims = mock(Claims.class);
        when(jwt.extractAllClaims("token")).thenReturn(claims);
        when(claims.get("authorities", String.class)).thenReturn("ROLE_EMPLOYER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> routes.jwtAuthFilter(request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void authenticatedRequestVerifiesTokenOnceAndForwardsVerifiedClaims() {
        JwtUtil jwt = spy(new JwtUtil());
        RouteConfig routes = new RouteConfig(jwt);
        String token = token(signingKey(), Instant.now().plusSeconds(300));
        MockHttpServletRequest servletRequest = new MockHttpServletRequest(
                "PATCH", "/api/applications/1/status");
        servletRequest.addHeader(JwtConstant.JWT_HEADER, JwtConstant.TOKEN_PREFIX + token);
        servletRequest.addHeader("X-User-Id", "999");
        servletRequest.addHeader("X-User-Email", "untrusted@example.com");
        servletRequest.addHeader("X-User-Role", "ROLE_ADMIN");

        ServerRequest filtered = routes.jwtAuthFilter(ServerRequest.create(servletRequest, List.of()));

        assertEquals("42", filtered.headers().firstHeader("X-User-Id"));
        assertEquals("employer@example.com", filtered.headers().firstHeader("X-User-Email"));
        assertEquals("ROLE_EMPLOYER", filtered.headers().firstHeader("X-User-Role"));
        verify(jwt).extractAllClaims(token);
        verifyNoMoreInteractions(jwt);
    }

    @Test
    void invalidExpiredAndIncorrectlySignedTokensRemainUnauthorized() {
        RouteConfig routes = new RouteConfig(new JwtUtil());
        SecretKey differentKey = Jwts.SIG.HS256.key().build();
        List<String> tokens = List.of(
                "malformed-token",
                token(signingKey(), Instant.now().minusSeconds(60)),
                token(differentKey, Instant.now().plusSeconds(300)));

        for (String token : tokens) {
            MockHttpServletRequest servletRequest = new MockHttpServletRequest(
                    "PATCH", "/api/applications/1/status");
            servletRequest.addHeader(JwtConstant.JWT_HEADER, JwtConstant.TOKEN_PREFIX + token);

            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> routes.jwtAuthFilter(ServerRequest.create(servletRequest, List.of())));

            assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        }
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(JwtConstant.SECRET_KEY.getBytes());
    }

    private static String token(SecretKey key, Instant expiration) {
        return Jwts.builder()
                .claim("email", "employer@example.com")
                .claim("authorities", "ROLE_EMPLOYER")
                .claim("userId", 42L)
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }
}
