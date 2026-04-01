package com.financetracker.finance_tracker.ai.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.common.metrics.AppMetrics;
import com.financetracker.finance_tracker.transaction.entity.Transaction;
import com.financetracker.finance_tracker.transaction.repository.TransactionRepo;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "true", matchIfMissing = true)
public class AiFraudDetectionService {

    private final ChatClient chatClient;
    private final TransactionRepo transactionRepo;
    private final AppMetrics appMetrics;

    private static final int MONTHS_LOOKBACK = 3;
    private static final BigDecimal MULTIPLIER_THRESHOLD = new BigDecimal("3.0");
    private static final long REPEAT_TIME_MINUTES = 10;
    private static final BigDecimal FRAUD_SCORE_THRESHOLD = new BigDecimal("0.75");

    public AiFraudDetectionService(ChatClient.Builder chatClientBuilder, TransactionRepo transactionRepo, AppMetrics appMetrics) {
        this.chatClient = chatClientBuilder.build();
        this.transactionRepo = transactionRepo;
        this.appMetrics = appMetrics;
    }

    public FraudAssessment analyze(UUID userId, String category, BigDecimal amount, LocalDateTime transactionDate) {
        long startNanos = System.nanoTime();
        BigDecimal average = calculateCategoryAverage(userId, category);
        boolean suspiciousByRules = checkRuleBasedSuspicion(userId, category, amount, average, transactionDate);
        if (suspiciousByRules) {
            appMetrics.incrementCounter("ai.fraud.rule.flagged");
        }

        String aiPrompt = buildPrompt(category, average, amount);
        String aiResponse = callAiModel(aiPrompt);
        FraudScore parsed = parseAiResponse(aiResponse);

        boolean isFraud = suspiciousByRules || parsed.score.compareTo(FRAUD_SCORE_THRESHOLD) > 0;
        appMetrics.incrementCounter("ai.fraud.analyzed", "result", isFraud ? "fraud" : "clear");
        appMetrics.recordDuration("ai.fraud.latency", startNanos);

        return FraudAssessment.builder()
                .score(parsed.score)
                .reason(parsed.reason)
                .isFraud(isFraud)
                .build();
    }

    private BigDecimal calculateCategoryAverage(UUID userId, String category) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(MONTHS_LOOKBACK);
        BigDecimal total = transactionRepo.sumAmountByUserIdAndCategoryAndDateBetween(
                userId, category, threeMonthsAgo, LocalDateTime.now());

        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        List<Transaction> transactions = transactionRepo.findByUserIdAndDateBetween(
                userId, threeMonthsAgo, LocalDateTime.now());

        long categoryCount = transactions.stream()
                .filter(t -> category.equalsIgnoreCase(t.getCategory()))
                .count();

        if (categoryCount == 0) {
            return BigDecimal.ZERO;
        }

        return total.divide(new BigDecimal(categoryCount), 2, java.math.RoundingMode.HALF_UP);
    }

    private boolean checkRuleBasedSuspicion(UUID userId, String category, BigDecimal amount,
            BigDecimal average, LocalDateTime transactionDate) {
        if (average.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal threshold = average.multiply(MULTIPLIER_THRESHOLD);
            if (amount.compareTo(threshold) > 0) {
                log.debug("Transaction amount {} exceeds 3x average {} for category {}", amount, average, category);
                return true;
            }
        }

        LocalDateTime tenMinutesAgo = transactionDate.minusMinutes(REPEAT_TIME_MINUTES);
        List<Transaction> recentTransactions = transactionRepo.findByUserIdAndDateBetween(
                userId, tenMinutesAgo, transactionDate);

        boolean repeatedAmount = recentTransactions.stream()
                .anyMatch(t -> t.getAmount().equals(amount));

        if (repeatedAmount) {
            log.debug("Same amount {} repeated within 10 minutes for user {}", amount, userId);
            return true;
        }

        return false;
    }

    private String buildPrompt(String category, BigDecimal average, BigDecimal amount) {
        String safeCategory = (category == null || category.isBlank()) ? "Other" : category.trim();
        String safeAverage = average == null ? "0" : average.toPlainString();
        String safeAmount = amount == null ? "0" : amount.toPlainString();

        return "User's average " + safeCategory + " transaction is " + safeAverage
                + ". New transaction: " + safeAmount
                + ". Is this suspicious? Reply with: FRAUD_SCORE: 0.0-1.0 | REASON: brief reason";
    }

    private String callAiModel(String prompt) {
        long startNanos = System.nanoTime();
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception ex) {
            appMetrics.incrementCounter("ai.fraud.ai_call", "result", "fallback");
            log.warn("AI fraud detection call failed; defaulting to low score", ex);
            return "FRAUD_SCORE: 0.2 | REASON: Could not analyze due to system error";
        } finally {
            appMetrics.recordDuration("ai.fraud.ai_call.latency", startNanos);
        }
    }

    private FraudScore parseAiResponse(String response) {
        if (response == null || response.isBlank()) {
            return new FraudScore(BigDecimal.ZERO, "Unable to determine fraud status");
        }

        BigDecimal score = extractScore(response);
        String reason = extractReason(response);

        return new FraudScore(score, reason);
    }

    private BigDecimal extractScore(String response) {
        Pattern pattern = Pattern.compile("FRAUD_SCORE:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            try {
                BigDecimal parsed = new BigDecimal(matcher.group(1));
                if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                    return BigDecimal.ZERO;
                }
                if (parsed.compareTo(BigDecimal.ONE) > 0) {
                    return BigDecimal.ONE;
                }
                return parsed;
            } catch (NumberFormatException ex) {
                log.warn("Could not parse fraud score from: {}", matcher.group(1), ex);
                return BigDecimal.ZERO;
            }
        }

        return BigDecimal.ZERO;
    }

    private String extractReason(String response) {
        Pattern pattern = Pattern.compile("REASON:\\s*(.+?)(?:\\||$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String reason = matcher.group(1).trim();
            if (reason.length() > 200) {
                return reason.substring(0, 200).trim();
            }
            return reason;
        }

        return "No specific reason provided";
    }

    @Data
    @Builder
    public static class FraudAssessment {
        private BigDecimal score;
        private String reason;
        private boolean isFraud;
    }

    private static class FraudScore {
        final BigDecimal score;
        final String reason;

        FraudScore(BigDecimal score, String reason) {
            this.score = score;
            this.reason = reason;
        }
    }

}
