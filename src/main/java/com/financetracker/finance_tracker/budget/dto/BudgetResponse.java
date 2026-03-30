package com.financetracker.finance_tracker.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class BudgetResponse {

    private UUID id;
    private UUID userId;
    private String category;
    private BigDecimal limitAmount;
    private int month;
    private int year;   
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal CurrentSpending;
    private BigDecimal RemainingAmount;
    private BigDecimal percentageUsed;
    
}
