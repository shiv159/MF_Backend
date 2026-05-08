package com.mutualfunds.api.mutual_fund.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        CorrelationIdHolder.set(correlationId);
        MDC.put(CorrelationIdHolder.MDC_KEY, correlationId);
        response.setHeader(CorrelationIdHolder.HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdHolder.MDC_KEY);
            CorrelationIdHolder.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String existing = request.getHeader(CorrelationIdHolder.HEADER_NAME);
        if (existing == null || existing.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return existing.trim();
    }
}
