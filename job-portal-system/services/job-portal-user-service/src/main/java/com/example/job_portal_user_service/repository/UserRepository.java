package com.example.job_portal_user_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.job_portal_user_service.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByEmail(String email);

    boolean existsByEmail(String email);
}
