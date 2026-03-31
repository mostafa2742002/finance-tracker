package com.financetracker.finance_tracker.alert.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "alerts")
@Data
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "user_id" ,nullable = false)
    private UUID userId;

    @Column(name = "type", length = 30, nullable = false)
    private String type; 

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    @Column(name = "transaction_id", nullable = true)
    private UUID transactionId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
}
