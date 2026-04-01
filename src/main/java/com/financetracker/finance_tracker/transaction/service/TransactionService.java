package com.financetracker.finance_tracker.transaction.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.financetracker.finance_tracker.ai.entity.TransactionCreatedEvent;
import com.financetracker.finance_tracker.budget.service.BudgetService;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.transaction.dto.TransactionRequest;
import com.financetracker.finance_tracker.transaction.dto.TransactionResponse;
import com.financetracker.finance_tracker.transaction.entity.Transaction;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepo transactionRepo;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final BudgetService budgetService;

    public ApiResponse<TransactionResponse> createTransaction(TransactionRequest request, UUID userId) {

        log.info("Creating transaction for user {}: amount={}, description='{}', category={}, type={}, date={}, clientId={}",
                userId, request.getAmount(), request.getDescription(), request.getCategory(), request.getType(), request.getDate(), request.getClientId());

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

        applicationEventPublisher.publishEvent(new TransactionCreatedEvent(
                this,
                savedTransaction.getId(),
                userId,
                savedTransaction.getAmount(),
                savedTransaction.getDescription(),
                savedTransaction.getCategory()
        ));

        budgetService.checkBudgetAfterTransaction(userId, savedTransaction.getCategory(), savedTransaction.getAmount(), savedTransaction.getDate());

        TransactionResponse response = TransactionResponse.fromEntity(savedTransaction);
        ApiResponse<TransactionResponse> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        apiResponse.setMessage("Transaction created successfully");
        apiResponse.setData(response);

        log.info("Transaction created successfully for user {}: transactionId={}, amount={}, category={}",
                userId, savedTransaction.getId(), savedTransaction.getAmount(), savedTransaction.getCategory());

        return apiResponse;
    }

    public ApiResponse<Page<TransactionResponse>> getUserTransactions(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String category,
            Pageable pageable) {

        log.info("Fetching transactions for user {} with filters - startDate: {}, endDate: {}, category: {}, page: {}, size: {}",
                userId, startDate, endDate, category, pageable.getPageNumber(), pageable.getPageSize());

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

        log.info("Fetched {} transactions for user {} with applied filters", responsePage.getNumberOfElements(), userId);

        return new ApiResponse<>(true, "Transactions fetched successfully", responsePage);
    }

    public ApiResponse<TransactionResponse> getTransactionById(UUID transactionId, UUID userId) {
        
        log.info("Fetching transaction by ID for user {}: transactionId={}", userId, transactionId);

        Transaction transaction = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to transaction");
        }

        TransactionResponse response = TransactionResponse.fromEntity(transaction);
        return new ApiResponse<>(true, "Transaction fetched successfully", response);
    }

    public ApiResponse<TransactionResponse> updateTransaction(UUID transactionId, TransactionRequest request, UUID userId) {
        
        log.info("Updating transaction for user {}: transactionId={}, amount={}, description='{}', category={}, type={}, date={}",
                userId, transactionId, request.getAmount(), request.getDescription(), request.getCategory(), request.getType(), request.getDate());

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

        log.info("Transaction updated successfully for user {}: transactionId={}, amount={}, category={}",
                userId, updatedTransaction.getId(), updatedTransaction.getAmount(), updatedTransaction.getCategory());

        return new ApiResponse<>(true, "Transaction updated successfully", response);

    }

    public ApiResponse<Void> deleteTransaction(UUID transactionId, UUID userId) {
        
        log.info("Deleting transaction for user {}: transactionId={}", userId, transactionId);

        Transaction transaction = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to transaction");
        }

        transactionRepo.delete(transaction);

        log.info("Transaction deleted successfully for user {}: transactionId={}", userId, transactionId);

        return new ApiResponse<>(true, "Transaction deleted successfully", null);
    }    

}
