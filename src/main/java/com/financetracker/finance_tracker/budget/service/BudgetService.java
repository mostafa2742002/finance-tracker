package com.financetracker.finance_tracker.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.alert.entity.Alert.AlertType;
import com.financetracker.finance_tracker.alert.service.AlertService;
import com.financetracker.finance_tracker.budget.dto.BudgetRequest;
import com.financetracker.finance_tracker.budget.dto.BudgetResponse;
import com.financetracker.finance_tracker.budget.entity.Budget;
import com.financetracker.finance_tracker.budget.repository.BudgetRepository;
import com.financetracker.finance_tracker.common.metrics.AppMetrics;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepo transactionRepo;
    private final AlertService alertService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppMetrics appMetrics;

    public ApiResponse<BudgetResponse> createOrUpdate(BudgetRequest request, UUID userId) {
        long startNanos = System.nanoTime();
        
        try {
            log.info("Creating/updating budget for user {}: category={}, month={}, year={}, limitAmount={}",
                    userId, request.getCategory(), request.getMonth(), request.getYear(), request.getLimitAmount());

            Optional<Budget> existingBudgetOpt = budgetRepository.findByUserIdAndCategoryAndMonthAndYear(
                    userId, request.getCategory(), request.getMonth(), request.getYear());

            Budget budget;
            boolean existingBudget = existingBudgetOpt.isPresent();
            if (existingBudget) {
                
                budget = existingBudgetOpt.get();
                budget.setLimitAmount(request.getLimitAmount());
                budget.setUpdatedAt(LocalDateTime.now());
            } else {
                
                budget = new Budget();
                budget.setUserId(userId);
                budget.setCategory(request.getCategory());
                budget.setLimitAmount(request.getLimitAmount());
                budget.setMonth(request.getMonth());
                budget.setYear(request.getYear());
                budget.setCreatedAt(LocalDateTime.now());
            }

            Budget savedBudget = budgetRepository.save(budget);
            appMetrics.incrementCounter(existingBudget ? "budgets.updated" : "budgets.created");

            // calculate current spending and remaining amount for the response
            BigDecimal currentSpending = getCurrentSpending(userId, savedBudget.getCategory(), savedBudget.getMonth(), savedBudget.getYear());
            BigDecimal remainingAmount = savedBudget.getLimitAmount().subtract(currentSpending);
            BigDecimal percentageUsed = currentSpending.divide(savedBudget.getLimitAmount(), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));    
            


            BudgetResponse response = BudgetResponse.fromEntity(savedBudget);

            response.setCurrentSpending(currentSpending);
            response.setRemainingAmount(remainingAmount);
            response.setPercentageUsed(percentageUsed);
            
            ApiResponse<BudgetResponse> apiResponse = new ApiResponse<>();
            apiResponse.setSuccess(true);
            apiResponse.setData(response);
            
            log.info("Budget for user {} saved successfully: budgetId={}, category={}, month={}, year={}, limitAmount={}",
                    userId, savedBudget.getId(), savedBudget.getCategory(), savedBudget.getMonth(), savedBudget.getYear(), savedBudget.getLimitAmount());

            return apiResponse;
        } finally {
            appMetrics.recordDuration("budgets.create_or_update.latency", startNanos);
        }
    }

    public ApiResponse<List<BudgetResponse>> getBudgetsForMonth(int month, int year, UUID userId) {
        long startNanos = System.nanoTime();
        
        try {
            log.info("Retrieving budgets for user {} for month {} and year {}", userId, month, year);

            List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(userId, month, year);
            List<BudgetResponse> responses = budgets.stream()
                    .map(BudgetResponse::fromEntity)
                    .collect(Collectors.toList());

            for (BudgetResponse response : responses) {
                
                BigDecimal currentSpending = getCurrentSpending(userId, response.getCategory(), month, year);
                BigDecimal remainingAmount = response.getLimitAmount().subtract(currentSpending);
                BigDecimal percentageUsed = currentSpending.divide(response.getLimitAmount(), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));    
                
                response.setCurrentSpending(currentSpending);
                response.setRemainingAmount(remainingAmount);
                response.setPercentageUsed(percentageUsed);
            }

            ApiResponse<List<BudgetResponse>> apiResponse = new ApiResponse<>();
            apiResponse.setSuccess(true);
            apiResponse.setData(responses);
            appMetrics.incrementCounter("budgets.fetched");

            log.info("Retrieved {} budgets for user {} for month {} and year {}", responses.size(), userId, month, year);
            
            return apiResponse;
        } finally {
            appMetrics.recordDuration("budgets.fetch.latency", startNanos);
        }
    }


    public BigDecimal getCurrentSpending(UUID userId, String category, int month, int year) {
        String redisKey = String.format("spending:%s:%s:%d:%d", userId, category, year, month);
        BigDecimal cachedSpending = (BigDecimal) redisTemplate.opsForValue().get(redisKey);
        if (cachedSpending != null) {
            appMetrics.incrementCounter("budgets.spending.cache", "result", "hit");
            return cachedSpending;
        }
        appMetrics.incrementCounter("budgets.spending.cache", "result", "miss");

        LocalDateTime startOfMonth = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusSeconds(1);
        BigDecimal currentSpending = transactionRepo.sumAmountByUserIdAndCategoryOrAICategoryAndDateBetween(
                userId, category, category, startOfMonth, endOfMonth);

        redisTemplate.opsForValue().set(redisKey, currentSpending);
        return currentSpending;
    }

    public ApiResponse<Void> deleteBudget(UUID budgetId, UUID userId) {
        long startNanos = System.nanoTime();
        Optional<Budget> budgetOpt = budgetRepository.findById(budgetId);
        if (budgetOpt.isEmpty() || !budgetOpt.get().getUserId().equals(userId)) {
            appMetrics.incrementCounter("budgets.delete.denied");
            ApiResponse<Void> apiResponse = new ApiResponse<>();
            apiResponse.setSuccess(false);
            apiResponse.setMessage("Budget not found or unauthorized");
            return apiResponse;
        }

        budgetRepository.deleteById(budgetId);
        appMetrics.incrementCounter("budgets.deleted");
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);
        appMetrics.recordDuration("budgets.delete.latency", startNanos);
        return apiResponse;
    }

    public void checkBudgetAfterTransaction(UUID userId, String category, BigDecimal amount, LocalDateTime transactionDate){
        int month = transactionDate.getMonthValue();
        int year = transactionDate.getYear();
        Optional<Budget> budgetOpt = budgetRepository.findByUserIdAndCategoryAndMonthAndYear(userId, category, month, year);
        /*
        If spending >= 80% of limit → create BUDGET_WARNING alert
        If spending >= 100% → create OVERSPENDING aler
         */
        if (budgetOpt.isPresent()) {
            Budget budget = budgetOpt.get();
            BigDecimal currentSpending = getCurrentSpending(userId, category, month, year);
            if (currentSpending.compareTo(budget.getLimitAmount().multiply(BigDecimal.valueOf(0.8))) >= 0 &&
                currentSpending.compareTo(budget.getLimitAmount()) < 0) {
                appMetrics.incrementCounter("budget.alerts.created", "type", AlertType.BUDGET_WARNING.name().toLowerCase());
                appMetrics.incrementBudgetAlertsTriggered();
                alertService.createBudgetAlert(userId, category, currentSpending, budget.getLimitAmount(), AlertType.BUDGET_WARNING);
            } else if (currentSpending.compareTo(budget.getLimitAmount()) >= 0) {
                appMetrics.incrementCounter("budget.alerts.created", "type", AlertType.OVERSPENDING.name().toLowerCase());
                appMetrics.incrementBudgetAlertsTriggered();
                alertService.createBudgetAlert(userId, category, currentSpending, budget.getLimitAmount(), AlertType.OVERSPENDING);
            }
        }

    }

}
