package com.financetracker.finance_tracker.alert.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.alert.entity.Alert;
import com.financetracker.finance_tracker.alert.entity.Alert.AlertType;
import com.financetracker.finance_tracker.alert.repository.AlertRepository;
import com.financetracker.finance_tracker.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    public void createFraudAlert(UUID userId, UUID transactionId, BigDecimal fraudScore, String fraudReason) {
        log.warn("FRAUD ALERT for user {} on transaction {}: score={}, reason={}",
                userId, transactionId, fraudScore, fraudReason);

        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setType(AlertType.FRAUD_DETECTED);            
        alert.setMessage(String.format("Potential fraud detected on transaction %s. Score: %s. Reason: %s",
                transactionId, fraudScore, fraudReason));
        alert.setTransactionId(transactionId);
        alert.setRead(false);
        alert.setCreatedAt(java.time.LocalDateTime.now());      

        alertRepository.save(alert);
        // TODO: Send notification to user (email, SMS, push notification)
        // notificationService.sendFraudAlert(user, fraudScore, fraudReason);
    }

    public void createBudgetAlert(UUID userId, String category, BigDecimal currentSpending, BigDecimal budgetLimit, AlertType alertType) {
        log.info("BUDGET ALERT for user {}: category={}, currentSpending={}, budgetLimit={}",
                userId, category, currentSpending, budgetLimit);

        Alert alert = new Alert();
        alert.setUserId(userId);    
        alert.setType(alertType);

        if(alertType == AlertType.OVERSPENDING) {
            alert.setMessage(String.format("You have overspent in category %s. Current spending: %s exceeds your budget limit of %s.",
                    category, currentSpending, budgetLimit));
        } else if(alertType == AlertType.BUDGET_WARNING) {
            alert.setMessage(String.format("Warning: You are approaching your budget limit in category %s. Current spending: %s out of %s.",
                    category, currentSpending, budgetLimit));
        }
        alert.setRead(false);
        alert.setCreatedAt(java.time.LocalDateTime.now());

        alertRepository.save(alert);
    }

    public Page<Alert> getUserAlerts(UUID userId, int page, int size) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public void markAlertAsRead(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + alertId));
        alert.setRead(true);
        alertRepository.save(alert);
    }

    public long countUnreadAlerts(UUID userId) {
        return alertRepository.countByUserIdAndIsReadFalse(userId); 
    }

    public ApiResponse<Map<String, Long>> getUnreadAlertCounts(UUID userId) {
        long unreadCount = alertRepository.countByUserIdAndIsReadFalse(userId);
        Map<String, Long> response = Map.of("unreadCount", unreadCount);

        ApiResponse<Map<String, Long>> apiResponse = new ApiResponse<>();
        apiResponse.setSuccess(true);

        apiResponse.setData(response);
        return apiResponse;
    }


}
