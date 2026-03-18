package com.financetracker.finance_tracker.transaction.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.financetracker.finance_tracker.ai.entity.TransactionCreatedEvent;
import com.financetracker.finance_tracker.ai.service.AiEventListener;
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
    private final AiEventListener aiEventListener;

    public ApiResponse<TransactionResponse> createTransaction(TransactionRequest request, UUID userId) {

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

        aiEventListener.handleTransactionCreated(new TransactionCreatedEvent(
                this,
                savedTransaction.getId(),
                userId,
                savedTransaction.getAmount(),
                savedTransaction.getDescription(),
                savedTransaction.getCategory()
        ));

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

    public ApiResponse<TransactionResponse> getTransactionById(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to transaction");
        }

        TransactionResponse response = TransactionResponse.fromEntity(transaction);
        return new ApiResponse<>(true, "Transaction fetched successfully", response);
    }

    public ApiResponse<TransactionResponse> updateTransaction(UUID transactionId, TransactionRequest request, UUID userId) {
        Transaction transaction = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to transaction");   
        }

        transaction.setAmount(request.getAmount() != null ? request.getAmount() : transaction.getAmount());
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : transaction.getDescription ());
        transaction.setCategory(request.getCategory() != null ? request.getCategory() : transaction.getCategory());
        transaction.setType(request.getType() != null ? request.getType() : transaction.getType());
        transaction.setDate(request.getDate() != null ? request.getDate() : transaction.getDate());

        Transaction updatedTransaction = transactionRepo.save(transaction);
        TransactionResponse response = TransactionResponse.fromEntity(updatedTransaction);
        return new ApiResponse<>(true, "Transaction updated successfully", response);

    }

    public ApiResponse<Void> deleteTransaction(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to transaction");
        }

        transactionRepo.delete(transaction);
        return new ApiResponse<>(true, "Transaction deleted successfully", null);
    }    

}
