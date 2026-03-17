package com.financetracker.finance_tracker.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.financetracker.finance_tracker.common.exception.UserNotFoundException;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.transaction.dto.TransactionRequest;
import com.financetracker.finance_tracker.transaction.dto.TransactionResponse;
import com.financetracker.finance_tracker.transaction.service.TransactionService;
import com.financetracker.finance_tracker.user.entity.User;
import com.financetracker.finance_tracker.user.repository.UserRepo;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private UserRepo userRepo;

    private TransactionController controller;

    @BeforeEach
    void setUp() {
        controller = new TransactionController(transactionService, userRepo);
    }

    @Test
    void getUserTransactions_withValidAuth_returnsOkResponse() {
        String email = "user@example.com";
        UUID userId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null);
        User user = User.builder().id(userId).email(email).name("User").password("x").build();
        Pageable pageable = PageRequest.of(0, 10);
        ApiResponse<Page<TransactionResponse>> apiResponse = new ApiResponse<>(true, "ok",
                new PageImpl<>(java.util.List.of()));

        when(userRepo.findByEmail(email)).thenReturn(user);
        when(transactionService.getUserTransactions(eq(userId), any(), any(), any(), eq(pageable)))
                .thenReturn(apiResponse);

        ApiResponse<Page<TransactionResponse>> body = controller.getUserTransactions(null, null, null, pageable, auth)
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        verify(transactionService).getUserTransactions(userId, null, null, null, pageable);
    }

    @Test
    void createTransaction_whenUserMissing_throwsUserNotFoundException() {
        String email = "missing@example.com";
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null);

        when(userRepo.findByEmail(email)).thenReturn(null);

        TransactionRequest request = TransactionRequest.builder()
                .amount(BigDecimal.TEN)
                .type("EXPENSE")
                .date(LocalDateTime.now())
                .build();

        assertThrows(UserNotFoundException.class, () -> controller.createTransaction(request, auth));
    }

    @Test
    void getTransactionById_withValidAuth_delegatesToService() {
        String email = "user@example.com";
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null);
        User user = User.builder().id(userId).email(email).name("User").password("x").build();
        ApiResponse<TransactionResponse> apiResponse = new ApiResponse<>(true, "ok", null);

        when(userRepo.findByEmail(email)).thenReturn(user);
        when(transactionService.getTransactionById(txId, userId)).thenReturn(apiResponse);

        ApiResponse<TransactionResponse> body = controller.getTransactionById(txId, auth).getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        verify(transactionService).getTransactionById(txId, userId);
    }

    @Test
    void updateTransaction_withValidAuth_delegatesToService() {
        String email = "user@example.com";
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null);
        User user = User.builder().id(userId).email(email).name("User").password("x").build();
        TransactionRequest request = TransactionRequest.builder().description("updated").build();
        ApiResponse<TransactionResponse> apiResponse = new ApiResponse<>(true, "updated", null);

        when(userRepo.findByEmail(email)).thenReturn(user);
        when(transactionService.updateTransaction(txId, request, userId)).thenReturn(apiResponse);

        ApiResponse<TransactionResponse> body = controller.updateTransaction(request, txId, auth).getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        verify(transactionService).updateTransaction(txId, request, userId);
    }

    @Test
    void deleteTransaction_withValidAuth_delegatesToService() {
        String email = "user@example.com";
        UUID userId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(email, null);
        User user = User.builder().id(userId).email(email).name("User").password("x").build();
        ApiResponse<Void> apiResponse = new ApiResponse<>(true, "deleted", null);

        when(userRepo.findByEmail(email)).thenReturn(user);
        when(transactionService.deleteTransaction(txId, userId)).thenReturn(apiResponse);

        ApiResponse<Void> body = controller.deleteTransaction(txId, auth).getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isTrue();
        verify(transactionService).deleteTransaction(txId, userId);
    }
}
