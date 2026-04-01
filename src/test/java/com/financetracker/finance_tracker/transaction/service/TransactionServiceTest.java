package com.financetracker.finance_tracker.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.budget.service.BudgetService;
import com.financetracker.finance_tracker.common.metrics.AppMetrics;
import com.financetracker.finance_tracker.transaction.dto.TransactionResponse;
import com.financetracker.finance_tracker.transaction.entity.Transaction;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepo transactionRepo;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private BudgetService budgetService;

    @Mock
    private AppMetrics appMetrics;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void getUserTransactions_withoutFilters_usesUserIdQuery() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Transaction> transactionPage = new PageImpl<>(List.of(sampleTransaction(userId)));

        when(transactionRepo.findByUserId(userId, pageable)).thenReturn(transactionPage);

        ApiResponse<Page<TransactionResponse>> response = transactionService.getUserTransactions(
                userId,
                null,
                null,
                null,
                pageable);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getTotalElements()).isEqualTo(1);
        verify(transactionRepo).findByUserId(userId, pageable);
        verify(transactionRepo, never()).findByUserIdAndDateBetween(any(), any(), any(), any());
        verify(transactionRepo, never()).findByUserIdAndCategoryIgnoreCase(any(), any(), any());
    }

    @Test
    void getUserTransactions_withDateAndCategory_usesCombinedQuery() {
        UUID userId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 31, 23, 59);
        String category = " food ";
        Pageable pageable = PageRequest.of(0, 20);

        when(transactionRepo.findByUserIdAndDateBetweenAndCategoryIgnoreCase(userId, start, end, "food", pageable))
                .thenReturn(Page.empty(pageable));

        ApiResponse<Page<TransactionResponse>> response = transactionService.getUserTransactions(
                userId,
                start,
                end,
                category,
                pageable);

        assertThat(response.isSuccess()).isTrue();
        verify(transactionRepo).findByUserIdAndDateBetweenAndCategoryIgnoreCase(userId, start, end, "food", pageable);
    }

    @Test
    void getUserTransactions_withCategoryOnly_usesCategoryQuery() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        when(transactionRepo.findByUserIdAndCategoryIgnoreCase(userId, "food", pageable))
                .thenReturn(Page.empty(pageable));

        ApiResponse<Page<TransactionResponse>> response = transactionService.getUserTransactions(
                userId,
                null,
                null,
                " food ",
                pageable);

        assertThat(response.isSuccess()).isTrue();
        verify(transactionRepo).findByUserIdAndCategoryIgnoreCase(userId, "food", pageable);
    }

    @Test
    void getUserTransactions_withDateOnly_usesDateQuery() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 31, 23, 59);

        when(transactionRepo.findByUserIdAndDateBetween(userId, start, end, pageable))
                .thenReturn(Page.empty(pageable));

        ApiResponse<Page<TransactionResponse>> response = transactionService.getUserTransactions(
                userId,
                start,
                end,
                null,
                pageable);

        assertThat(response.isSuccess()).isTrue();
        verify(transactionRepo).findByUserIdAndDateBetween(userId, start, end, pageable);
    }

    @Test
    void getUserTransactions_withOnlyStartDate_throwsValidationException() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(ValidationException.class, () -> transactionService.getUserTransactions(
                userId,
                LocalDateTime.now(),
                null,
                null,
                pageable));

        verify(transactionRepo, never()).findByUserId(eq(userId), any());
    }

    @Test
    void getUserTransactions_withStartAfterEnd_throwsValidationException() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime start = LocalDateTime.of(2026, 3, 31, 23, 59);
        LocalDateTime end = LocalDateTime.of(2026, 3, 1, 0, 0);

        assertThrows(ValidationException.class, () -> transactionService.getUserTransactions(
                userId,
                start,
                end,
                null,
                pageable));
    }

    @Test
    void createTransaction_withExistingClientId_returnsExistingTransaction() {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        Transaction existing = sampleTransaction(userId);
        existing.setClientId(clientId);

        com.financetracker.finance_tracker.transaction.dto.TransactionRequest request = com.financetracker.finance_tracker.transaction.dto.TransactionRequest
                .builder()
                .amount(BigDecimal.TEN)
                .type("EXPENSE")
                .date(LocalDateTime.now())
                .clientId(clientId)
                .build();

        when(transactionRepo.findByClientId(clientId)).thenReturn(Optional.of(existing));

        ApiResponse<TransactionResponse> response = transactionService.createTransaction(request, userId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("already exists");
        verify(transactionRepo, never()).save(any(Transaction.class));
    }

    @Test
    void getTransactionById_withDifferentUser_throwsRuntimeException() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        Transaction existing = sampleTransaction(ownerId);

        when(transactionRepo.findById(txId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionService.getTransactionById(txId, requesterId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void updateTransaction_withOwner_updatesAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        Transaction existing = sampleTransaction(userId);
        existing.setId(txId);

        com.financetracker.finance_tracker.transaction.dto.TransactionRequest request = com.financetracker.finance_tracker.transaction.dto.TransactionRequest
                .builder()
                .description("Updated")
                .category("Bills")
                .amount(BigDecimal.valueOf(150))
                .type("EXPENSE")
                .date(LocalDateTime.of(2026, 3, 10, 10, 0))
                .build();

        when(transactionRepo.findById(txId)).thenReturn(Optional.of(existing));
        when(transactionRepo.save(existing)).thenReturn(existing);

        ApiResponse<TransactionResponse> response = transactionService.updateTransaction(txId, request, userId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(existing.getDescription()).isEqualTo("Updated");
        assertThat(existing.getCategory()).isEqualTo("Bills");
        verify(transactionRepo).save(existing);
    }

    @Test
    void deleteTransaction_withOwner_deletesTransaction() {
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        Transaction existing = sampleTransaction(userId);

        when(transactionRepo.findById(txId)).thenReturn(Optional.of(existing));

        ApiResponse<Void> response = transactionService.deleteTransaction(txId, userId);

        assertThat(response.isSuccess()).isTrue();
        verify(transactionRepo).delete(existing);
    }

    private Transaction sampleTransaction(UUID userId) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(BigDecimal.valueOf(99.50))
                .category("food")
                .description("Lunch")
                .type("EXPENSE")
                .date(LocalDateTime.now())
                .build();
    }
}
