package com.financetracker.finance_tracker.alert.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AlertService {

    public void createFraudAlert(UUID userId, UUID transactionId, BigDecimal fraudScore, String fraudReason) {
        log.warn("FRAUD ALERT for user {} on transaction {}: score={}, reason={}",
                userId, transactionId, fraudScore, fraudReason);

        // TODO: Persist alert to database
        // Alert alert = Alert.builder()
        // .userId(userId)
        // .transactionId(transactionId)
        // .type("FRAUD_DETECTION")
        // .severity("HIGH")
        // .message(fraudReason)
        // .fraudScore(fraudScore)
        // .createdAt(LocalDateTime.now())
        // .isRead(false)
        // .build();
        // alertRepository.save(alert);

        // TODO: Send notification to user (email, SMS, push notification)
        // notificationService.sendFraudAlert(user, fraudScore, fraudReason);
    }
}
