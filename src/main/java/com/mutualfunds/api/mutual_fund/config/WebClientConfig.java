package com.mutualfunds.api.mutual_fund.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient (async/reactive HTTP client)
 * Provides centralized WebClient bean for all HTTP communication
 */
@Configuration
public class WebClientConfig {

    /**
     * Provides a WebClient bean for async HTTP requests
     * Used for calling external services like Python ETL API
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}