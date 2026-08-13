package com.example.job_portal_user_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.domain.UserRole;
import com.example.domain.UserStatus;
import com.example.job_portal_user_service.model.User;
import com.example.job_portal_user_service.repository.UserRepository;

@RequiredArgsConstructor
@Component
public class DataInitializationComponent implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeAdminUser();
    }

    private void initializeAdminUser(){
        String adminEmail = "admin@gmail.com";

        if(!userRepository.existsByEmail(adminEmail)){
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setFullName("admin");
            admin.setPassword(passwordEncoder.encode("admin"));
            admin.setRole(UserRole.ROLE_ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
        }
    }
}