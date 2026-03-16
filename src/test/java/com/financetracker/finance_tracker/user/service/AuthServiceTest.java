package com.financetracker.finance_tracker.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.financetracker.finance_tracker.common.exception.DuplicateEmailException;
import com.financetracker.finance_tracker.common.exception.InvalidEmailOrPassword;
import com.financetracker.finance_tracker.common.jwt.JwtUtils;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.user.dto.AuthResponse;
import com.financetracker.finance_tracker.user.dto.LoginRequest;
import com.financetracker.finance_tracker.user.dto.SignupRequest;
import com.financetracker.finance_tracker.user.repository.RefreshTokenRepo;
import com.financetracker.finance_tracker.user.repository.UserRepo;

import io.jsonwebtoken.Claims;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class AuthServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("finance_tracker_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private RefreshTokenRepo refreshTokenRepo;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void signup_withValidInput_returnsSuccessAndTokens() {
        SignupRequest request = new SignupRequest();
        request.setUsername("Sasa");
        request.setEmail("sasa@example.com");
        request.setPassword("secret123");

        ApiResponse<AuthResponse> response = authService.signup(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User registered successfully");
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getAccessToken()).isNotBlank();
        assertThat(response.getData().getRefreshToken()).isNotBlank();
        assertThat(response.getData().getExpiresIn()).isEqualTo(jwtUtils.getAccessTokenValidity());

        assertThat(userRepo.existsByEmail("sasa@example.com")).isTrue();
        assertThat(refreshTokenRepo.findAll()).hasSize(1);
    }

    @Test
    void signup_withDuplicateEmail_throwsDuplicateEmailException() {
        SignupRequest first = new SignupRequest();
        first.setUsername("Sasa");
        first.setEmail("sasa@example.com");
        first.setPassword("secret123");
        authService.signup(first);

        SignupRequest second = new SignupRequest();
        second.setUsername("Another");
        second.setEmail("sasa@example.com");
        second.setPassword("another123");

        assertThrows(DuplicateEmailException.class, () -> authService.signup(second));
    }

    @Test
    void login_withWrongPassword_throwsInvalidEmailOrPassword() {
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setUsername("Sasa");
        signupRequest.setEmail("sasa@example.com");
        signupRequest.setPassword("secret123");
        authService.signup(signupRequest);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("sasa@example.com");
        loginRequest.setPassword("wrong-password");

        assertThrows(InvalidEmailOrPassword.class, () -> authService.login(loginRequest));
    }

    @Test
    void signup_generatesJwt_withExpectedClaims() {
        SignupRequest request = new SignupRequest();
        request.setUsername("Sasa");
        request.setEmail("sasa@example.com");
        request.setPassword("secret123");

        ApiResponse<AuthResponse> response = authService.signup(request);
        String accessToken = response.getData().getAccessToken();

        assertThat(accessToken).isNotBlank();

        Claims claims = jwtUtils.extractAllClaims(accessToken);

        assertThat(claims.getSubject()).isEqualTo("sasa@example.com");
        assertThat(claims.get("userEmail", String.class)).isEqualTo("sasa@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }
}