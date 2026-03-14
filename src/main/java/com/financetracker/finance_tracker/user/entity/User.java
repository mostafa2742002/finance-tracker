package com.financetracker.finance_tracker.user.entity;

import java.sql.Timestamp;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class User {
    @Id
    private UUID id;
    
    @NotNull
    private String name;
    
    @NotNull
    @Column(unique = true)
    private String email;

    @NotNull
    private String password;
    
    @NotNull
    private String role;
    
    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;
    
    @Column(name = "updated_at")
    private Timestamp updatedAt;

}
