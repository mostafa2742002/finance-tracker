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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Operations for creating, updating, deleting, and reading user transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepo userRepo;

    @PostMapping
    @Operation(summary = "Create a transaction", description = "Creates a new transaction for the authenticated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests")
    })
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
    @Operation(summary = "Get transaction by identifier", description = "Returns a single transaction owned by the authenticated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @PathVariable UUID transactionId,
            Authentication authentication) {
        ApiResponse<TransactionResponse> response = transactionService.getTransactionById(
                transactionId,
                getUserIdFromAuth(authentication));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List user transactions", description = "Returns paginated transactions for the authenticated user with optional filters")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transactions fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid filter values"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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
    @Operation(summary = "Update a transaction", description = "Updates an existing transaction owned by the authenticated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> updateTransaction(
            @RequestBody TransactionRequest request,
            @PathVariable UUID transactionId,
            Authentication authentication) {
        ApiResponse<TransactionResponse> response = transactionService.updateTransaction(transactionId, request,
                getUserIdFromAuth(authentication));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete a transaction", description = "Deletes a transaction owned by the authenticated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transaction deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction not found")
    })
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
