package com.example.job_portal_user_service.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dto.response.UserResponse;
import com.example.job_portal_user_service.mapper.UserMapper;
import com.example.job_portal_user_service.model.User;
import com.example.job_portal_user_service.payload.UpdateUserRequest;
import com.example.job_portal_user_service.service.UserService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/profile")
    public ResponseEntity<UserResponse> getProfile(
            @RequestHeader("X-User-Email") String email) throws Exception {
        User user = userService.getUserByEmail(email);
        return ResponseEntity.ok(UserMapper.toDTO(user));
    }

    @PutMapping("/api/users/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @RequestHeader("X-User-Email") String email,
            @RequestBody UpdateUserRequest req) throws Exception {
        return ResponseEntity.ok(userService.updateProfile(email, req));
    }

    @GetMapping("/api/users/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long userId) throws Exception {
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(UserMapper.toDTO(user));
    }

    @GetMapping("/api/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() throws Exception {
        return ResponseEntity.ok(UserMapper.toDTOList(userService.getAllUsers()));
    }

    @PatchMapping("/api/users/{userId}/suspend")
    public ResponseEntity<UserResponse> suspendUser(
            @PathVariable Long userId) throws Exception {
        return ResponseEntity.ok(userService.suspendUser(userId));
    }

    @PatchMapping("/api/users/{userId}/activate")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable Long userId) throws Exception {
        return ResponseEntity.ok(userService.activateUser(userId));
    }

    @DeleteMapping("/api/users/{userId}")
    public ResponseEntity<UserResponse> deleteUser(
            @PathVariable Long userId) throws Exception {
            return ResponseEntity.ok(userService.deleteUser(userId));
    }

}