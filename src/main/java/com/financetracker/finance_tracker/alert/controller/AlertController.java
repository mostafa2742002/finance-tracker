package com.financetracker.finance_tracker.alert.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.finance_tracker.alert.dto.AlertResponse;
import com.financetracker.finance_tracker.alert.service.AlertService;
import com.financetracker.finance_tracker.common.exception.UserNotFoundException;
import com.financetracker.finance_tracker.common.response.ApiResponse;
import com.financetracker.finance_tracker.user.entity.User;
import com.financetracker.finance_tracker.user.repository.UserRepo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final UserRepo userRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AlertResponse>>> getAlerts(
            @RequestParam(required = false) Boolean isRead,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        UUID userId = getUserIdFromAuth(authentication);
        Page<AlertResponse> page = alertService.getUserAlerts(userId, isRead, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Alerts fetched successfully", page));
    }

    @GetMapping("/count/unread")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        long unreadCount = alertService.getUnreadCount(userId);
        return ResponseEntity
                .ok(new ApiResponse<>(true, "Unread count fetched successfully", Map.of("unreadCount", unreadCount)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {

        UUID userId = getUserIdFromAuth(authentication);
        alertService.markAsRead(id, userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Alert marked as read", null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        int updated = alertService.markAllAsRead(userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "All alerts marked as read", Map.of("updated", updated)));
    }

    private UUID getUserIdFromAuth(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Authenticated user not found");
        }
        return user.getId();
    }
}
