package com.financetracker.finance_tracker.budget.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.financetracker.finance_tracker.budget.entity.Budget;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByUserIdAndCategoryAndMonthAndYear(UUID userId, String category, int month, int year);
    List<Budget> findByUserIdAndMonthAndYear(UUID userId, int month, int year);
}
