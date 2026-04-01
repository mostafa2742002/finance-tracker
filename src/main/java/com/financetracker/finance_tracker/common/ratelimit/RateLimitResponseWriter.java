package com.financetracker.finance_tracker.common.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitResponseWriter {

    public void writeTooManyRequests(HttpServletResponse response, String message, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(buildJsonMessage(message));
    }

    private String buildJsonMessage(String message) {
        String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"success\":false,\"message\":\"" + escapedMessage + "\"}";
    }
}
