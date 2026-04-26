package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

public enum ToolDetailLevel {
    COMPACT,
    ANALYST;

    public static ToolDetailLevel from(String raw, ToolDetailLevel fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ToolDetailLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
