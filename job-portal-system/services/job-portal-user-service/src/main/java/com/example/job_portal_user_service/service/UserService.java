package com.example.job_portal_user_service.service;

import java.util.List;

import com.example.dto.response.UserResponse;
import com.example.job_portal_user_service.model.User;
import com.example.job_portal_user_service.payload.UpdateUserRequest;

public interface UserService {

    User getUserByEmail(String email) throws Exception;

    User getUserById(Long id) throws Exception;

    List<User> getAllUsers();

    UserResponse updateProfile(String email, UpdateUserRequest req) throws Exception;

//    admin action

    UserResponse suspendUser(Long id) throws Exception;
    UserResponse activateUser(Long id) throws Exception;
    UserResponse deleteUser(Long id) throws Exception;
}