package com.financetracker.finance_tracker.common.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class AppMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger budgetAlertsTriggered = new AtomicInteger();
    private final AtomicInteger aiEventsInFlight = new AtomicInteger();

    public AppMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("budget.alerts.triggered", budgetAlertsTriggered, AtomicInteger::get)
                .description("Total budget alerts triggered since application start")
                .register(meterRegistry);

        Gauge.builder("ai.events.inflight", aiEventsInFlight, AtomicInteger::get)
                .description("Number of AI transaction events currently being processed")
                .register(meterRegistry);
    }

    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    public void recordDuration(String name, long startNanos, String... tags) {
        Timer.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void incrementBudgetAlertsTriggered() {
        budgetAlertsTriggered.incrementAndGet();
    }

    public void incrementAiEventsInFlight() {
        aiEventsInFlight.incrementAndGet();
    }

    public void decrementAiEventsInFlight() {
        aiEventsInFlight.updateAndGet(current -> Math.max(0, current - 1));
    }
}
