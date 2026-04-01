package com.financetracker.finance_tracker.ai.service;

import java.math.BigDecimal;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.common.metrics.AppMetrics;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "true", matchIfMissing = true)
public class AiAdviceService {

    private final ChatClient chatClient;
    private final AppMetrics appMetrics;

    public AiAdviceService(ChatClient.Builder chatClientBuilder, AppMetrics appMetrics) {
        this.chatClient = chatClientBuilder.build();
        this.appMetrics = appMetrics;
    }

    public String generateAdvice(BigDecimal monthlySpent, String category, BigDecimal overBudgetPercentage) {
        String safeSpent = monthlySpent == null ? "0" : monthlySpent.toPlainString();
        String safeCategory = (category == null || category.isBlank()) ? "Other" : category.trim();
        String safeOverBudget = overBudgetPercentage == null ? "0" : overBudgetPercentage.toPlainString();

        String prompt = "A user spent " + safeSpent
                + " on " + safeCategory
                + " this month, which is " + safeOverBudget
                + "% over their budget. Give one specific, actionable tip to save money in this category. "
                + "Be concise (1-2 sentences).";

        long startNanos = System.nanoTime();
        String advice;
        try {
            advice = chatClient.prompt().user(prompt).call().content();
        } finally {
            appMetrics.recordDuration("ai.advice.latency", startNanos);
        }
        if (advice == null || advice.isBlank()) {
            appMetrics.incrementCounter("ai.advice.generated", "result", "fallback");
            return "Review recent spending in this category and set a smaller weekly cap to stay within budget.";
        }

        String normalized = advice.trim();
        if (normalized.length() > 300) {
            log.debug("AI advice response was long; trimming to 300 chars");
            appMetrics.incrementCounter("ai.advice.generated", "result", "trimmed");
            return normalized.substring(0, 300).trim();
        }
        appMetrics.incrementCounter("ai.advice.generated", "result", "success");
        return normalized;
    }
}
