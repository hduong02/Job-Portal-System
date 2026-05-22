package com.example.job_portal_user_service.payload;

import lombok.Data;

@Data
public class UpdateUserRequest {

    private String fullName;

    private String phone;

    private String profileImage;
}
