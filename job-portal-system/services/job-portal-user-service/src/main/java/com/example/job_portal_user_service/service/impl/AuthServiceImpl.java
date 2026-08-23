package com.example.job_portal_user_service.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.domain.UserRole;
import com.example.domain.UserStatus;
import com.example.job_portal_user_service.mapper.UserMapper;
import com.example.job_portal_user_service.model.User;
import com.example.job_portal_user_service.payload.AuthResponse;
import com.example.job_portal_user_service.payload.LoginRequest;
import com.example.job_portal_user_service.payload.SignupRequest;
import com.example.job_portal_user_service.repository.UserRepository;
import com.example.job_portal_user_service.security.CustomUserDetailsService;
import com.example.job_portal_user_service.security.JwtProvider;
import com.example.job_portal_user_service.service.AuthService;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    public AuthResponse signup(SignupRequest req) throws Exception {

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new Exception("Email Already registered : " + req.getEmail());
        }

        if (req.getRole() == UserRole.ROLE_ADMIN) {
            throw new Exception("cannot self-register as role admin");
        }

        User user = User.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .phone(req.getPhone())
                .lastLogin(LocalDateTime.now())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getPassword());

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtProvider.generateToken(authentication, savedUser.getId());

        AuthResponse res = new AuthResponse();
        res.setTitle("welcome " + savedUser.getFullName());
        res.setMessage("Registered Successfully");
        res.setJwt(jwt);
        res.setUser(UserMapper.toDTO(savedUser));

        return res;
    }

    @Override
    public AuthResponse login(LoginRequest req) throws Exception {
        Authentication authentication = authenticate(
                req.getEmail(), req.getPassword()
        );

        User user = userRepository.findByEmail(req.getEmail());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account is not active");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtProvider.generateToken(authentication,user.getId());

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        AuthResponse response = new AuthResponse();
        response.setTitle("Welcome back " + user.getFullName());
        response.setMessage("Login Successfully");
        response.setJwt(jwt);
        response.setUser(UserMapper.toDTO(user));

        return response;
    }

    private Authentication authenticate(String email, String password) throws Exception {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        if (userDetails == null) {
            throw new Exception("user not found with email " + email);
        }

        if (!passwordEncoder.matches(password, userDetails.getPassword())){
            throw new Exception("Invalid password");
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
    }
}
