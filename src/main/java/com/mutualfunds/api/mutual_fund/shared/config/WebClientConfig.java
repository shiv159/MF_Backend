package com.mutualfunds.api.mutual_fund.shared.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 180000)
                .responseTimeout(java.time.Duration.ofMillis(180000));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
