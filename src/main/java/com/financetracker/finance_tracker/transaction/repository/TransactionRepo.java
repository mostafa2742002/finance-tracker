package com.financetracker.finance_tracker.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.financetracker.finance_tracker.transaction.entity.Transaction;

@Repository
public interface TransactionRepo extends JpaRepository<Transaction, UUID> {
    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndDateBetween(UUID userId, LocalDateTime start, LocalDateTime end,
            Pageable pageable);

    Page<Transaction> findByUserIdAndCategory(UUID userId, String category, Pageable pageable);

    List<Transaction> findByUserIdAndDateBetween(UUID userId, LocalDateTime start, LocalDateTime end); // for reports

    Optional<Transaction> findByClientId(UUID clientId); // for idempotency check

    BigDecimal sumAmountByUserIdAndCategoryAndDateBetween(
            UUID userId, String category, LocalDateTime start, LocalDateTime end); // for budget checking
}
