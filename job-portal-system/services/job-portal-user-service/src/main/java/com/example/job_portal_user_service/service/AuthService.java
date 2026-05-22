package com.example.job_portal_user_service.service;

import com.example.job_portal_user_service.payload.AuthResponse;
import com.example.job_portal_user_service.payload.LoginRequest;
import com.example.job_portal_user_service.payload.SignupRequest;

public interface AuthService {

    AuthResponse signup(SignupRequest req) throws Exception;
    AuthResponse login(LoginRequest req) throws Exception;
}
