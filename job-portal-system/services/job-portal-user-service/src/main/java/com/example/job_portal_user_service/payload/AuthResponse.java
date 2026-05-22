package com.example.job_portal_user_service.payload;

import com.example.dto.response.UserResponse;

import lombok.Data;

@Data
public class AuthResponse {

    private String jwt;
    private String title;
    private String message;
    private UserResponse user;

}