package com.financetracker.finance_tracker.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.common.metrics.AppMetrics;

@Service
public class RateLimitService {

    private static final int GENERAL_REQUEST_LIMIT = 100;
    private static final int AI_REQUEST_LIMIT = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final Map<String, WindowState> generalRequestWindows = new ConcurrentHashMap<>();
    private final Map<String, WindowState> aiRequestWindows = new ConcurrentHashMap<>();
    private final AppMetrics appMetrics;

    public RateLimitService(AppMetrics appMetrics) {
        this.appMetrics = appMetrics;
    }

    public RateLimitDecision checkGeneralRequestLimit(String userKey) {
        return checkLimit(generalRequestWindows, userKey, GENERAL_REQUEST_LIMIT, "general");
    }

    public RateLimitDecision checkAiRequestLimit(String userKey) {
        return checkLimit(aiRequestWindows, userKey, AI_REQUEST_LIMIT, "ai");
    }

    private RateLimitDecision checkLimit(Map<String, WindowState> windows, String userKey, int limit, String limitType) {
        Instant now = Instant.now();
        WindowState state = windows.compute(userKey, (key, existing) -> refreshWindow(existing, now));

        synchronized (state) {
            if (state.requestCount < limit) {
                state.requestCount++;
                appMetrics.incrementCounter("rate.limit.allowed", "type", limitType);
                return RateLimitDecision.permit();
            }

            long retryAfterSeconds = Duration.between(now, state.windowStart.plus(RATE_LIMIT_WINDOW)).getSeconds();
            appMetrics.incrementCounter("rate.limit.blocked", "type", limitType);
            return RateLimitDecision.rejected(retryAfterSeconds);
        }
    }

    private WindowState refreshWindow(WindowState existing, Instant now) {
        if (existing == null || now.isAfter(existing.windowStart.plus(RATE_LIMIT_WINDOW))) {
            return new WindowState(now);
        }
        return existing;
    }

    private static final class WindowState {
        private final Instant windowStart;
        private int requestCount;

        private WindowState(Instant windowStart) {
            this.windowStart = windowStart;
            this.requestCount = 0;
        }
    }
}
