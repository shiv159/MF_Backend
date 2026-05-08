package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class LangChain4jToolExecutionGuard {

    private final AiWorkflowProperties properties;
    private final ObjectMapper objectMapper;

    private final java.util.concurrent.ExecutorService toolExecutor = Executors.newFixedThreadPool(
            4,
            new ThreadFactory() {
                private final AtomicInteger sequence = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "lc4j-tool-" + sequence.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    TurnBudget startTurn() {
        return new TurnBudget();
    }

    GuardDecision evaluate(TurnBudget budget, String toolName) {
        if (budget.executedCount >= properties.getMaxToolCallsPerTurn()) {
            return GuardDecision.reject("TOOL_BUDGET_EXHAUSTED", "Tool budget exhausted for this turn");
        }
        if (properties.isDuplicateToolSuppression() && budget.executedNames.contains(toolName)) {
            return GuardDecision.reject("DUPLICATE_TOOL_SUPPRESSED", "Duplicate tool call was suppressed");
        }
        budget.executedCount++;
        budget.executedNames.add(toolName);
        return GuardDecision.permit();
    }

    String executeWithBudget(String toolName, ThrowingSupplier<String> supplier) {
        try {
            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }, toolExecutor);
            String rawResult = task.get(properties.getPerToolTimeoutMs(), TimeUnit.MILLISECONDS);
            return applyOutputBudget(toolName, rawResult);
        } catch (TimeoutException timeoutException) {
            log.warn("lc4j_tool_timeout toolName={} timeoutMs={}", toolName, properties.getPerToolTimeoutMs());
            return errorPayload("TIMEOUT", toolName, "Tool execution timed out");
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            String message = cause == null ? "Tool execution failed" : cause.getMessage();
            return errorPayload("ERROR", toolName, message == null ? "Tool execution failed" : message);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return errorPayload("INTERRUPTED", toolName, "Tool execution interrupted");
        }
    }

    String rejectedPayload(String toolName, GuardDecision decision) {
        return errorPayload(decision.code(), toolName, decision.message());
    }

    private String applyOutputBudget(String toolName, String rawResult) {
        if (rawResult == null) {
            return objectMapper.createObjectNode()
                    .put("status", "OK")
                    .put("toolName", toolName)
                    .toString();
        }
        int budget = Math.max(1, properties.getMaxToolResultChars());
        if (rawResult.length() <= budget) {
            return rawResult;
        }
        return objectMapper.createObjectNode()
                .put("status", "PARTIAL")
                .put("toolName", toolName)
                .put("truncated", true)
                .put("originalLength", rawResult.length())
                .put("message", "Tool output exceeded per-call budget and was truncated")
                .put("snippet", rawResult.substring(0, budget))
                .toString();
    }

    private String errorPayload(String status, String toolName, String message) {
        return objectMapper.createObjectNode()
                .put("status", status)
                .put("toolName", toolName)
                .put("message", message == null ? "Tool execution failed" : message)
                .toString();
    }

    static final class TurnBudget {
        private int executedCount = 0;
        private final Set<String> executedNames = new LinkedHashSet<>();
    }

    record GuardDecision(boolean allowed, String code, String message) {
        static GuardDecision permit() {
            return new GuardDecision(true, "OK", "allowed");
        }

        static GuardDecision reject(String code, String message) {
            return new GuardDecision(false, code, message);
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @PreDestroy
    void shutdown() {
        toolExecutor.shutdownNow();
    }
}
