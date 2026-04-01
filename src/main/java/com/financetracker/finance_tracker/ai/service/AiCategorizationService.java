package com.financetracker.finance_tracker.ai.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.financetracker.finance_tracker.common.metrics.AppMetrics;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AiCategorizationService {

	private static final Duration CATEGORY_CACHE_TTL = Duration.ofHours(1);
	private static final String CACHE_PREFIX = "ai:category:";

	private static final Map<String, String> ALLOWED_CATEGORIES = Map.ofEntries(
			Map.entry("food", "Food"),
			Map.entry("transport", "Transport"),
			Map.entry("entertainment", "Entertainment"),
			Map.entry("housing", "Housing"),
			Map.entry("healthcare", "Healthcare"),
			Map.entry("shopping", "Shopping"),
			Map.entry("education", "Education"),
			Map.entry("utilities", "Utilities"),
			Map.entry("income", "Income"),
			Map.entry("other", "Other"));

	private final ChatClient chatClient;
	private final StringRedisTemplate redisTemplate;
    private final AppMetrics appMetrics;

	public AiCategorizationService(ObjectProvider<ChatModel> chatModelProvider, StringRedisTemplate redisTemplate, AppMetrics appMetrics) {
		ChatModel chatModel = chatModelProvider.getIfAvailable();
		this.chatClient = chatModel != null ? ChatClient.create(chatModel) : null;
		this.redisTemplate = redisTemplate;
        this.appMetrics = appMetrics;
	}

	public String categorize(String description, BigDecimal amount) {
		String normalizedDescription = description == null ? "" : description.trim();
		String cacheKey = CACHE_PREFIX + sha256Hex(normalizedDescription.toLowerCase(Locale.ROOT));

		String cachedCategory = getCachedCategory(cacheKey);
		if (cachedCategory != null) {
            appMetrics.incrementCounter("ai.categorization.cache", "result", "hit");
            appMetrics.incrementCounter("ai.categorization.completed", "source", "cache", "category", cachedCategory.toLowerCase(Locale.ROOT));
			return cachedCategory;
		}
        appMetrics.incrementCounter("ai.categorization.cache", "result", "miss");

		if (chatClient == null) {
            appMetrics.incrementCounter("ai.categorization.completed", "source", "fallback", "category", "other");
            log.warn("AI chat model is not available; defaulting category to Other");
            return "Other";
        }

		String prompt = buildPrompt(normalizedDescription, amount);
        long startNanos = System.nanoTime();
		String aiResponse;
        try {
            aiResponse = chatClient.prompt().user(prompt).call().content();
        } finally {
            appMetrics.recordDuration("ai.categorization.latency", startNanos);
        }

		String category = normalizeCategory(aiResponse);
		cacheCategory(cacheKey, category);
        appMetrics.incrementCounter("ai.categorization.completed", "source", "ai", "category", category.toLowerCase(Locale.ROOT));
		return category;
	}

	private String getCachedCategory(String key) {
		try {
			String value = redisTemplate.opsForValue().get(key);
			if (value == null || value.isBlank()) {
				return null;
			}
			return normalizeCategory(value);
		} catch (Exception ex) {
			log.warn("Unable to read AI category cache for key {}", key, ex);
			return null;
		}
	}

	private void cacheCategory(String key, String category) {
		try {
			redisTemplate.opsForValue().set(key, category, CATEGORY_CACHE_TTL);
		} catch (Exception ex) {
			log.warn("Unable to write AI category cache for key {}", key, ex);
		}
	}

	private String buildPrompt(String description, BigDecimal amount) {
		String safeAmount = amount == null ? "0" : amount.toPlainString();
		return "Categorize this transaction into one of: "
				+ "[Food, Transport, Entertainment, Housing, Healthcare, Shopping, Education, Utilities, Income, Other]. "
				+ "Transaction: " + description + ", Amount: " + safeAmount + ". "
				+ "Return only the category name.";
	}

	private String normalizeCategory(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Other";
		}

		String normalized = raw.replace("\"", "")
				.replace("'", "")
				.replace("`", "")
				.trim();

		String direct = ALLOWED_CATEGORIES.get(normalized.toLowerCase(Locale.ROOT));
		if (direct != null) {
			return direct;
		}

		String lower = normalized.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String> entry : ALLOWED_CATEGORIES.entrySet()) {
			if (lower.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		return "Other";
	}

	private String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (Exception ex) {
			throw new IllegalStateException("Could not hash AI cache key", ex);
		}
	}
}
