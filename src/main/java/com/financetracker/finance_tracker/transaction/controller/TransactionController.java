package com.financetracker.finance_tracker.transaction.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.finance_tracker.common.exception.UserNotFoundException;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.transaction.dto.TransactionRequest;
import com.financetracker.finance_tracker.transaction.dto.TransactionResponse;
import com.financetracker.finance_tracker.transaction.service.TransactionService;
import com.financetracker.finance_tracker.user.entity.User;
import com.financetracker.finance_tracker.user.repository.UserRepo;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepo userRepo;

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }

        ApiResponse<TransactionResponse> response = transactionService.createTransaction(request, user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @PathVariable UUID transactionId,
            Authentication authentication) {
        ApiResponse<TransactionResponse> response = transactionService.getTransactionById(
                transactionId,
                getUserIdFromAuth(authentication));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getUserTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String category,
            @PageableDefault(sort = "date", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        String email = authentication.getName();
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }

        ApiResponse<Page<TransactionResponse>> response = transactionService.getUserTransactions(
                user.getId(),
                startDate,
                endDate,
                category,
                pageable);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> updateTransaction(
            @RequestBody TransactionRequest request,
            @PathVariable UUID transactionId,
            Authentication authentication) {
        ApiResponse<TransactionResponse> response = transactionService.updateTransaction(transactionId, request,
                getUserIdFromAuth(authentication));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(
            @PathVariable UUID transactionId,
            Authentication authentication) {
        ApiResponse<Void> response = transactionService.deleteTransaction(transactionId,
                getUserIdFromAuth(authentication));
        return ResponseEntity.ok(response);
    }

    private UUID getUserIdFromAuth(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }
        return user.getId();
    }
}
