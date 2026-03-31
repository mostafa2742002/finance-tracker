package com.financetracker.finance_tracker.alert.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.financetracker.finance_tracker.alert.entity.Alert;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<Alert> findByUserIdAndIsRead(UUID userId, boolean isRead, Pageable pageable);
    long countByUserIdAndIsReadFalse(UUID userId);
    
}
