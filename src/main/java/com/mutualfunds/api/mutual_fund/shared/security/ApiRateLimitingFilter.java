package com.mutualfunds.api.mutual_fund.shared.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiRateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiter apiRateLimiter;

    public ApiRateLimitingFilter(RateLimiterRegistry rateLimiterRegistry) {
        this.apiRateLimiter = rateLimiterRegistry.rateLimiter("api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!apiRateLimiter.acquirePermission()) {
            throw RequestNotPermitted.createRequestNotPermitted(apiRateLimiter);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !requestUri.startsWith("/api");
    }
}