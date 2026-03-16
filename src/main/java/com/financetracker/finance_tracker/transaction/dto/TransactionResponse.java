package com.financetracker.finance_tracker.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.financetracker.finance_tracker.transaction.entity.Transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionResponse {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private String description;
    private String category;
    private String type;
    private LocalDateTime date;
    private String formattedDate;
    private boolean isFraud;
    private BigDecimal fraudScore;
    private String fraudReason;
    private String aiCategory;
    private String aiAdvice;
    private boolean aiProcessed;
    private UUID clientId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TransactionResponse fromEntity(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .category(transaction.getCategory())
                .type(transaction.getType())
                .date(transaction.getDate())
                .formattedDate(formatDate(transaction.getDate()))
                .isFraud(transaction.isFraud())
                .fraudScore(transaction.getFraudScore())
                .fraudReason(transaction.getFraudReason())
                .aiCategory(transaction.getAiCategory())
                .aiAdvice(transaction.getAiAdvice())
                .aiProcessed(transaction.isAiProcessed())
                .clientId(transaction.getClientId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    private static String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_FORMATTER);
    }
}
