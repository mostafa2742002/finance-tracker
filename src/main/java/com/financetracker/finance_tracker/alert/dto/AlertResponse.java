package com.financetracker.finance_tracker.alert.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.financetracker.finance_tracker.alert.entity.Alert.AlertType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertResponse {
    private UUID id;
    private UUID userId;
    private AlertType type;
    private String message;
    private UUID transactionId;
    private boolean isRead;
    private LocalDateTime createdAt;
}
