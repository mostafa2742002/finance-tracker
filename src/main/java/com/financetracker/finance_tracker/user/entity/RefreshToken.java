package com.financetracker.finance_tracker.user.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    private String id;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(nullable = false, unique = true)
    private String token;
    @Column(name = "expiry", nullable = false)
    private Timestamp expiry;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;
}
