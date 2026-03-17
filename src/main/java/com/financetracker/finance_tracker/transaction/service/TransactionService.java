package com.financetracker.finance_tracker.transaction.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.transaction.dto.TransactionRequest;
import com.financetracker.finance_tracker.transaction.dto.TransactionResponse;
import com.financetracker.finance_tracker.transaction.entity.Transaction;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class TransactionService {

    private final TransactionRepo transactionRepo;

    public ApiResponse<TransactionResponse> create(TransactionRequest request, UUID userId) {
        
        if(transactionRepo.findByClientId(request.getClientId()).isPresent()) {
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
}
