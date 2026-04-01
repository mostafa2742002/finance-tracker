package com.financetracker.finance_tracker.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.financetracker.finance_tracker.common.metrics.AppMetrics;

class RateLimitServiceTest {

    private final AppMetrics appMetrics = mock(AppMetrics.class);
    private final RateLimitService rateLimitService = new RateLimitService(appMetrics);

    @Test
    void checkGeneralRequestLimit_allowsFirstHundredRequests() {
        RateLimitDecision lastDecision = null;

        for (int i = 0; i < 100; i++) {
            lastDecision = rateLimitService.checkGeneralRequestLimit("user@example.com");
        }

        assertThat(lastDecision).isNotNull();
        assertThat(lastDecision.allowed()).isTrue();
    }

    @Test
    void checkGeneralRequestLimit_blocksRequestAfterLimit() {
        for (int i = 0; i < 100; i++) {
            rateLimitService.checkGeneralRequestLimit("user@example.com");
        }

        RateLimitDecision blockedDecision = rateLimitService.checkGeneralRequestLimit("user@example.com");

        assertThat(blockedDecision.allowed()).isFalse();
        assertThat(blockedDecision.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    void checkAiRequestLimit_blocksAfterTenRequests() {
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkAiRequestLimit("user-1");
        }

        RateLimitDecision blockedDecision = rateLimitService.checkAiRequestLimit("user-1");

        assertThat(blockedDecision.allowed()).isFalse();
        assertThat(blockedDecision.retryAfterSeconds()).isGreaterThan(0);
    }
}
