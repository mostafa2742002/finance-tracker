package com.financetracker.finance_tracker.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionRequest {

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    private String description;

    private String category;

    @NotBlank(message = "Type is required")
    private String type;

    @NotNull(message = "Date is required")
    private LocalDateTime date;

    private UUID clientId;
}
