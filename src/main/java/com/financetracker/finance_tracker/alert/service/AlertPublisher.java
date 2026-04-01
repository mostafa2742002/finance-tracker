package com.financetracker.finance_tracker.alert.service;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.alert.dto.AlertResponse;
import com.financetracker.finance_tracker.common.metrics.AppMetrics;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final AppMetrics appMetrics;

    public void pushAlert(UUID userId, AlertResponse alert) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/alerts",
                alert);
        appMetrics.incrementCounter("alerts.published");
    }
}
