package com.financetracker.finance_tracker.alert.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class AlertRequest {

    private UUID userId;
    private UUID transactionId;
    private BigDecimal fraudScore;
    private String fraudReason;
    private String category;
    private BigDecimal currentSpending;
    private BigDecimal budgetLimit;
}
