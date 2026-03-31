package com.financetracker.finance_tracker.budget.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class BudgetRequest {

    private String category;
    private BigDecimal limitAmount;
    private int month;
    private int year;

}
