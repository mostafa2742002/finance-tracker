package com.financetracker.finance_tracker.ai.service;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.financetracker.finance_tracker.ai.entity.TransactionCreatedEvent;
import com.financetracker.finance_tracker.ai.service.AiFraudDetectionService.FraudAssessment;
import com.financetracker.finance_tracker.alert.service.AlertService;
import com.financetracker.finance_tracker.transaction.entity.Transaction;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiEventListener {

    private final AiCategorizationService categorizationService;
    private final AiFraudDetectionService fraudDetectionService;
    private final AlertService alertService;
    private final TransactionRepo transactionRepo;

    @EventListener
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        log.info("Processing AI event for transaction {} of user {}", event.getTransactionId(), event.getUserId());

        Transaction transaction = transactionRepo.findById(event.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + event.getTransactionId()));

        try {
            String aiCategory = categorizationService.categorize(
                    event.getDescription(),
                    event.getAmount());
            transaction.setAiCategory(aiCategory);

            FraudAssessment fraudAssessment = fraudDetectionService.analyze(
                    event.getUserId(),
                    transaction.getCategory(),
                    event.getAmount(),
                    transaction.getDate());

            transaction.setFraud(fraudAssessment.isFraud());
            transaction.setFraudScore(fraudAssessment.getScore());
            transaction.setFraudReason(fraudAssessment.getReason());

            if (fraudAssessment.isFraud()) {
                alertService.createFraudAlert(
                        event.getUserId(),
                        event.getTransactionId(),
                        fraudAssessment.getScore(),
                        fraudAssessment.getReason());
            }

            transaction.setAiProcessed(true);
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepo.save(transaction);

            log.info("AI processing completed for transaction {}: category={}, fraud={}",
                    event.getTransactionId(), aiCategory, fraudAssessment.isFraud());

        } catch (Exception ex) {
            log.error("Error processing AI event for transaction {}", event.getTransactionId(), ex);
            transaction.setAiProcessed(false);
            transactionRepo.save(transaction);
            throw new RuntimeException("AI event processing failed", ex);
        }
    }
}
