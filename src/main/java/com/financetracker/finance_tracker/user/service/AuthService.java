package com.financetracker.finance_tracker.user.service;

import java.sql.Timestamp;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.financetracker.finance_tracker.common.metrics.AppMetrics;
import com.financetracker.finance_tracker.common.exception.DuplicateEmailException;
import com.financetracker.finance_tracker.common.exception.ExpiredRefreshTokenException;
import com.financetracker.finance_tracker.common.exception.InvalidEmailOrPassword;
import com.financetracker.finance_tracker.common.exception.InvalidRefreshTokenException;
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
    private final AppMetrics appMetrics;

    @Transactional
    public ApiResponse<AuthResponse> signup(@Valid SignupRequest request) {
        long startNanos = System.nanoTime();
        try {
            if (userRepo.existsByEmail(request.getEmail())) {
                appMetrics.incrementCounter("auth.signup.failed", "reason", "duplicate_email");
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
            String refreshTokenHash = hashRefreshToken(refreshToken);
            Timestamp expiry = new Timestamp(System.currentTimeMillis() + jwtUtils.getRefreshTokenValidity());

            RefreshToken refreshTokenEntity = RefreshToken.builder()
                    .userId(savedUser.getId())
                    .token(refreshTokenHash)
                    .expiry(expiry)
                    .createdAt(now)
                    .build();
            refreshTokenRepo.save(refreshTokenEntity);

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtUtils.getAccessTokenValidity())
                    .build();
            appMetrics.incrementCounter("auth.signup.success");

            return new ApiResponse<>(true, "User registered successfully", authResponse);
        } finally {
            appMetrics.recordDuration("auth.signup.latency", startNanos);
        }
    }

    @Transactional
    public ApiResponse<AuthResponse> login(@Valid LoginRequest request) {
        long startNanos = System.nanoTime();
        try {
            User user = userRepo.findByEmail(request.getEmail());
            if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                appMetrics.incrementCounter("auth.login.failed", "reason", "invalid_credentials");
                throw new InvalidEmailOrPassword("Invalid email or password");
            }

            refreshTokenRepo.deleteByUserId(user.getId());

            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    user.getPassword(),
                    List.of(new SimpleGrantedAuthority(user.getRole())));

            String accessToken = jwtUtils.generateAccessToken(userDetails);

            String refreshToken = jwtUtils.generateRefreshToken();
            String refreshTokenHash = hashRefreshToken(refreshToken);
            Timestamp expiry = new Timestamp(System.currentTimeMillis() + jwtUtils.getRefreshTokenValidity());

            RefreshToken refreshTokenEntity = RefreshToken.builder()
                    .userId(user.getId())
                    .token(refreshTokenHash)
                    .expiry(expiry)
                    .createdAt(new Timestamp(System.currentTimeMillis()))
                    .build();
            refreshTokenRepo.save(refreshTokenEntity);

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtUtils.getAccessTokenValidity())
                    .build();
            appMetrics.incrementCounter("auth.login.success");

            return new ApiResponse<>(true, "Login successful", authResponse);
        } finally {
            appMetrics.recordDuration("auth.login.latency", startNanos);
        }
    }

    @Transactional
    public ApiResponse<AuthResponse> refreshToken(String refreshToken) {
        long startNanos = System.nanoTime();
        try {
            String refreshTokenHash = hashRefreshToken(refreshToken);
            RefreshToken tokenEntity = refreshTokenRepo.findByToken(refreshTokenHash)
                    .orElseThrow(() -> {
                        appMetrics.incrementCounter("auth.refresh.failed", "reason", "invalid_refresh_token");
                        return new InvalidRefreshTokenException("Invalid refresh token");
                    });

            if (tokenEntity.getExpiry().before(new Timestamp(System.currentTimeMillis()))) {
                refreshTokenRepo.delete(tokenEntity);
                appMetrics.incrementCounter("auth.refresh.failed", "reason", "expired_refresh_token");
                throw new ExpiredRefreshTokenException("Refresh token has expired");
            }
            refreshTokenRepo.delete(tokenEntity);

            User user = userRepo.findById(tokenEntity.getUserId())
                    .orElseThrow(() -> {
                        appMetrics.incrementCounter("auth.refresh.failed", "reason", "missing_user");
                        return new InvalidRefreshTokenException("User not found for refresh token");
                    });

            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    user.getPassword(),
                    List.of(new SimpleGrantedAuthority(user.getRole())));

            String newAccessToken = jwtUtils.generateAccessToken(userDetails);

            String newRefreshToken = jwtUtils.generateRefreshToken();
            String newRefreshTokenHash = hashRefreshToken(newRefreshToken);
            Timestamp expiry = new Timestamp(System.currentTimeMillis() + jwtUtils.getRefreshTokenValidity());
            RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                    .userId(user.getId())
                    .token(newRefreshTokenHash)
                    .expiry(expiry)
                    .createdAt(new Timestamp(System.currentTimeMillis()))
                    .build();
            refreshTokenRepo.save(newRefreshTokenEntity);

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn(jwtUtils.getAccessTokenValidity())
                    .build();
            appMetrics.incrementCounter("auth.refresh.success");

            return new ApiResponse<>(true, "Access token refreshed successfully", authResponse);
        } finally {
            appMetrics.recordDuration("auth.refresh.latency", startNanos);
        }
    }

    @Transactional
    public ApiResponse<String> logout(String refreshToken) {
        long startNanos = System.nanoTime();
        try {
            String refreshTokenHash = hashRefreshToken(refreshToken);
            RefreshToken tokenEntity = refreshTokenRepo.findByToken(refreshTokenHash)
                    .orElseThrow(() -> {
                        appMetrics.incrementCounter("auth.logout.failed", "reason", "invalid_refresh_token");
                        return new InvalidRefreshTokenException("Invalid refresh token");
                    });
            refreshTokenRepo.delete(tokenEntity);
            appMetrics.incrementCounter("auth.logout.success");

            return new ApiResponse<>(true, "Logged out successfully");
        } finally {
            appMetrics.recordDuration("auth.logout.latency", startNanos);
        }
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }
}
