package com.financetracker.finance_tracker.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginRequest {

    @Email(message = "Invalid email format")
    @NotNull(message = "Email is required")
    private String email;

    @NotNull(message = "Password is required")
    private String password;
}
