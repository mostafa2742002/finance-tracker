package com.financetracker.finance_tracker.user.service;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.financetracker.finance_tracker.common.exception.DuplicateEmailException;
import com.financetracker.finance_tracker.common.exception.InvalidEmailOrPassword;
import com.financetracker.finance_tracker.common.jwt.JwtUtils;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.user.dto.AuthResponse;
import com.financetracker.finance_tracker.user.dto.LoginRequest;
import com.financetracker.finance_tracker.user.dto.SignupRequest;
import com.financetracker.finance_tracker.user.entity.RefreshToken;
import com.financetracker.finance_tracker.user.entity.User;
import com.financetracker.finance_tracker.user.repository.RefreshTokenRepo;
import com.financetracker.finance_tracker.user.repository.UserRepo;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Service
@Validated
@AllArgsConstructor
public class AuthService {

    private final UserRepo userRepo;
    private final RefreshTokenRepo refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Transactional
    public ApiResponse<AuthResponse> signup(@Valid SignupRequest request) {
        if (userRepo.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email is already in use");
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        User user = User.builder()
                .name(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .createdAt(now)
                .updatedAt(now)
                .build();

        User savedUser = userRepo.save(user);
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                savedUser.getEmail(),
                savedUser.getPassword(),
                List.of(new SimpleGrantedAuthority(savedUser.getRole())));

        String accessToken = jwtUtils.generateAccessToken(
                userDetails);

        String refreshToken = jwtUtils.generateRefreshToken();
        Timestamp expiry = new Timestamp(System.currentTimeMillis() + jwtUtils.getRefreshTokenValidity());

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(savedUser.getId())
                .token(refreshToken)
                .expiry(expiry)
                .createdAt(now)
                .build();
        refreshTokenRepo.save(refreshTokenEntity);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getAccessTokenValidity())
                .build();

        return new ApiResponse<>(true, "User registered successfully", authResponse);
    }

    public ApiResponse<AuthResponse> login(@Valid LoginRequest request) {
        User user = userRepo.findByEmail(request.getEmail());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidEmailOrPassword("Invalid email or password");
        }

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole())));

        String accessToken = jwtUtils.generateAccessToken(userDetails);

        String refreshToken = jwtUtils.generateRefreshToken();
        Timestamp expiry = new Timestamp(System.currentTimeMillis() + jwtUtils.getRefreshTokenValidity());

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiry(expiry)
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .build();
        refreshTokenRepo.save(refreshTokenEntity);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtils.getAccessTokenValidity())
                .build();

        return new ApiResponse<>(true, "Login successful", authResponse);
    }
}
