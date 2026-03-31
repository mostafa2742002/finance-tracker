package com.financetracker.finance_tracker.budget.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.finance_tracker.budget.dto.BudgetRequest;
import com.financetracker.finance_tracker.budget.dto.BudgetResponse;
import com.financetracker.finance_tracker.budget.service.BudgetService;
import com.financetracker.finance_tracker.common.exception.UserNotFoundException;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.user.repository.UserRepo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final UserRepo userRepo;

    @PostMapping
    public ResponseEntity<ApiResponse<BudgetResponse>> createOrUpdateBudget(@Valid @RequestBody BudgetRequest request,
                Authentication authentication) {
        String email = authentication.getName();
        var user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }       
        ApiResponse<BudgetResponse> response = budgetService.createOrUpdate(request, user.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{budgetId}")
    public ResponseEntity<ApiResponse<Void>> deleteBudget(@PathVariable String budgetId, Authentication authentication) {
        String email = authentication.getName();
        var user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }
        ApiResponse<Void> response = budgetService.deleteBudget(UUID.fromString(budgetId), user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{month}/{year}")
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> getBudgetsForMonth(
            @PathVariable int month,
            @PathVariable int year,
            Authentication authentication) {
        String email = authentication.getName();
        var user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }
        ApiResponse<List<BudgetResponse>> response = budgetService.getBudgetsForMonth(month, year, user.getId());
        return ResponseEntity.ok(response);
    }

    

}
