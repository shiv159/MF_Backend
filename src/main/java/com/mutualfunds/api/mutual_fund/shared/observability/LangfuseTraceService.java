package com.mutualfunds.api.mutual_fund.shared.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class LangfuseTraceService {

    private final Tracer tracer;

    public LangfuseTraceService(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable(GlobalOpenTelemetry::get);
        this.tracer = openTelemetry.getTracer("com.mutualfunds.api.langfuse");
    }

    public <T> T traceChatTurn(
            String traceName,
            UUID userId,
            String sessionId,
            String route,
            String engine,
            String input,
            Supplier<T> operation) {
        Span span = tracer.spanBuilder(traceName).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            setIfPresent(span, "langfuse.trace.name", traceName);
            setIfPresent(span, "langfuse.user.id", userId == null ? null : userId.toString());
            setIfPresent(span, "langfuse.session.id", sessionId);
            setIfPresent(span, "langfuse.trace.metadata.route", route);
            setIfPresent(span, "langfuse.trace.metadata.engine", engine);
            setIfPresent(span, "langfuse.observation.input", input);
            T result = operation.get();
            setIfPresent(span, "langfuse.observation.output", result == null ? null : result.toString());
            return result;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getMessage() == null ? "chat turn failed" : ex.getMessage());
            throw ex;
        } finally {
            span.end();
        }
    }

    public void traceChatTurn(
            String traceName,
            UUID userId,
            String sessionId,
            String route,
            String engine,
            String input,
            Runnable operation) {
        traceChatTurn(traceName, userId, sessionId, route, engine, input, () -> {
            operation.run();
            return null;
        });
    }

    private void setIfPresent(Span span, String key, String value) {
        if (StringUtils.hasText(value)) {
            span.setAttribute(key, value);
        }
    }
}
