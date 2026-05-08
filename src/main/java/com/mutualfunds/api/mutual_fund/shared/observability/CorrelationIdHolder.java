package com.mutualfunds.api.mutual_fund.shared.observability;

public final class CorrelationIdHolder {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    private CorrelationIdHolder() {
    }

    public static void set(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            CORRELATION_ID.remove();
            return;
        }
        CORRELATION_ID.set(correlationId);
    }

    public static String get() {
        return CORRELATION_ID.get();
    }

    public static void clear() {
        CORRELATION_ID.remove();
    }
}
