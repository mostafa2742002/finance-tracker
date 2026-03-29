package com.financetracker.finance_tracker.ai.entity;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = false)
public class TransactionCreatedEvent extends ApplicationEvent{
    private final UUID transactionId;
    private final UUID userId;
    private final BigDecimal amount;
    private final String description;
    private final String category;

    public TransactionCreatedEvent(Object source, UUID transactionId, UUID userId, BigDecimal amount, String description, String category) {
        super(source);
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.description = description;
        this.category = category;
    }


}
