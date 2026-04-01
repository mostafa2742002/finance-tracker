package com.financetracker.finance_tracker.alert.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.finance_tracker.alert.dto.AlertResponse;
import com.financetracker.finance_tracker.alert.entity.Alert;
import com.financetracker.finance_tracker.alert.entity.Alert.AlertType;
import com.financetracker.finance_tracker.alert.repository.AlertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertPublisher alertPublisher;

    @Transactional
    public AlertResponse createAlert(UUID userId, AlertType type, String message, UUID transactionId) {
        log.info("Creating alert for user {}: type={}, message={}, transactionId={}",
                userId, type, message, transactionId);

        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setType(type);
        alert.setMessage(message);
        alert.setTransactionId(transactionId);
        alert.setRead(false);
        alert.setCreatedAt(java.time.LocalDateTime.now());

        Alert saved = alertRepository.save(alert);
        AlertResponse response = toResponse(saved);
        alertPublisher.pushAlert(userId, response);

        log.info("Alert created and published for user {}: alertId={}, type={}", userId, saved.getId(), type);

        return response;
    }

    public void createFraudAlert(UUID userId, UUID transactionId, BigDecimal fraudScore, String fraudReason) {
        log.warn("FRAUD ALERT for user {} on transaction {}: score={}, reason={}",
                userId, transactionId, fraudScore, fraudReason);

        createAlert(
                userId,
                AlertType.FRAUD_DETECTED,
                String.format("Potential fraud detected on transaction %s. Score: %s. Reason: %s",
                        transactionId, fraudScore, fraudReason),
                transactionId);

    }

    public void createBudgetAlert(UUID userId, String category, BigDecimal currentSpending, BigDecimal budgetLimit,
            AlertType alertType) {
        log.info("BUDGET ALERT for user {}: category={}, currentSpending={}, budgetLimit={}",
                userId, category, currentSpending, budgetLimit);

        String message;
        if (alertType == AlertType.OVERSPENDING) {
            message = String.format(
                    "You have overspent in category %s. Current spending: %s exceeds your budget limit of %s.",
                    category, currentSpending, budgetLimit);
        } else {
            message = String.format(
                    "Warning: You are approaching your budget limit in category %s. Current spending: %s out of %s.",
                    category, currentSpending, budgetLimit);
        }

        createAlert(userId, alertType, message, null);
    }

    public Page<AlertResponse> getUserAlerts(UUID userId, Boolean isRead, Pageable pageable) {
        log.info("Fetching alerts for user {}: isRead={}, page={}, size={}",
                userId, isRead, pageable.getPageNumber(), pageable.getPageSize());
                
        Page<Alert> alertsPage = isRead == null
                ? alertRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : alertRepository.findByUserIdAndIsRead(userId, isRead, pageable);

        return alertsPage.map(this::toResponse);
    }

    @Transactional
    public void markAsRead(UUID alertId, UUID userId) {
        log.info("Marking alert {} as read for user {}", alertId, userId);

        Alert alert = alertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Alert not found for id: " + alertId));

        alert.setRead(true);
        alertRepository.save(alert);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        log.info("Marking all alerts as read for user {}", userId);
        return alertRepository.markAllAsReadByUserId(userId);
    }

    public long getUnreadCount(UUID userId) {
        log.info("Getting unread alert count for user {}", userId);
        return alertRepository.countByUserIdAndIsReadFalse(userId);
    }

    private AlertResponse toResponse(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .userId(alert.getUserId())
                .type(alert.getType())
                .message(alert.getMessage())
                .transactionId(alert.getTransactionId())
                .isRead(alert.isRead())
                .createdAt(alert.getCreatedAt())
                .build();
    }

}
