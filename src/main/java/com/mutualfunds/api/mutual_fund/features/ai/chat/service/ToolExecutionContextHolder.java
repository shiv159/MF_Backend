package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import java.util.Optional;
import java.util.UUID;

public final class ToolExecutionContextHolder {

    private static final ThreadLocal<UUID> USER_CONTEXT = new ThreadLocal<>();

    private ToolExecutionContextHolder() {
    }

    public static void setUserId(UUID userId) {
        USER_CONTEXT.set(userId);
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(USER_CONTEXT.get());
    }

    public static void clear() {
        USER_CONTEXT.remove();
    }
}