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
import com.financetracker.finance_tracker.common.metrics.AppMetrics;
import com.financetracker.finance_tracker.common.ratelimit.RateLimitDecision;
import com.financetracker.finance_tracker.common.ratelimit.RateLimitService;
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
    private final AppMetrics appMetrics;
    private final RateLimitService rateLimitService;

    @jakarta.annotation.PostConstruct
    void logInitialization() {
        log.info("AI event listener initialized and ready to process transaction events");
    }

    @EventListener
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        long startNanos = System.nanoTime();
        appMetrics.incrementAiEventsInFlight();
        log.info("Processing AI event for transaction {} of user {}", event.getTransactionId(), event.getUserId());

        Transaction transaction = transactionRepo.findById(event.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: " + event.getTransactionId()));

        try {
            RateLimitDecision aiRateLimitDecision = rateLimitService.checkAiRequestLimit(event.getUserId().toString());
            if (!aiRateLimitDecision.allowed()) {
                appMetrics.incrementCounter("ai.events.skipped", "reason", "rate_limit");
                log.warn("Skipping AI processing for transaction {} due to AI rate limit. Retry after {} seconds",
                        event.getTransactionId(), aiRateLimitDecision.retryAfterSeconds());
                transaction.setAiProcessed(false);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepo.save(transaction);
                return;
            }

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
                appMetrics.incrementCounter("ai.fraud.detected");
                alertService.createFraudAlert(
                        event.getUserId(),
                        event.getTransactionId(),
                        fraudAssessment.getScore(),
                        fraudAssessment.getReason());
            }

            transaction.setAiProcessed(true);
            transaction.setUpdatedAt(LocalDateTime.now());
            transactionRepo.save(transaction);
            appMetrics.incrementCounter("ai.events.processed", "status", "success");

            log.info("AI processing completed for transaction {}: category={}, fraud={}",
                    event.getTransactionId(), aiCategory, fraudAssessment.isFraud());

        } catch (Exception ex) {
            appMetrics.incrementCounter("ai.events.processed", "status", "failure");
            log.error("Error processing AI event for transaction {}", event.getTransactionId(), ex);
            transaction.setAiProcessed(false);
            transactionRepo.save(transaction);
            throw new RuntimeException("AI event processing failed", ex);
        } finally {
            appMetrics.recordDuration("ai.event.processing.latency", startNanos);
            appMetrics.decrementAiEventsInFlight();
        }
    }
}
