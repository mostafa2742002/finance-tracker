package com.financetracker.finance_tracker.transaction.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.transaction.dto.TransactionRequest;
import com.financetracker.finance_tracker.transaction.dto.TransactionResponse;
import com.financetracker.finance_tracker.transaction.entity.Transaction;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class TransactionService {

    private final TransactionRepo transactionRepo;

    public ApiResponse<TransactionResponse> create(TransactionRequest request, UUID userId) {

        if (transactionRepo.findByClientId(request.getClientId()).isPresent()) {
            Transaction existingTransaction = transactionRepo.findByClientId(request.getClientId()).get();
            TransactionResponse response = TransactionResponse.fromEntity(existingTransaction);
            ApiResponse<TransactionResponse> apiResponse = new ApiResponse<>();
            apiResponse.setSuccess(true);
            apiResponse.setMessage("Transaction already exists with the provided clientId");
            apiResponse.setData(response);
            return apiResponse;
        }

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(request.getAmount())
                .description(request.getDescription())
                .category(request.getCategory())
                .type(request.getType())
                .date(request.getDate())
                .clientId(request.getClientId())
                .build();

        Transaction savedTransaction = transactionRepo.save(transaction);
        TransactionResponse response = TransactionResponse.fromEntity(savedTransaction);
        ApiResponse<TransactionResponse> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setMessage("Transaction created successfully");
        apiResponse.setData(response);
        return apiResponse;
    }

    public ApiResponse<Page<TransactionResponse>> getUserTransactions(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String category,
            Pageable pageable) {

        boolean hasDateFilter = startDate != null || endDate != null;
        boolean hasCategoryFilter = StringUtils.hasText(category);

        if (hasDateFilter && (startDate == null || endDate == null)) {
            throw new ValidationException("Both startDate and endDate must be provided when filtering by date");
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ValidationException("startDate must be before or equal to endDate");
        }

        String normalizedCategory = hasCategoryFilter ? category.trim() : null;

        Page<Transaction> transactions;
        if (hasDateFilter && hasCategoryFilter) {
            transactions = transactionRepo.findByUserIdAndDateBetweenAndCategoryIgnoreCase(
                    userId,
                    startDate,
                    endDate,
                    normalizedCategory,
                    pageable);
        } else if (hasDateFilter) {
            transactions = transactionRepo.findByUserIdAndDateBetween(userId, startDate, endDate, pageable);
        } else if (hasCategoryFilter) {
            transactions = transactionRepo.findByUserIdAndCategoryIgnoreCase(userId, normalizedCategory, pageable);
        } else {
            transactions = transactionRepo.findByUserId(userId, pageable);
        }

        Page<TransactionResponse> responsePage = transactions.map(TransactionResponse::fromEntity);
        return new ApiResponse<>(true, "Transactions fetched successfully", responsePage);
    }

}
