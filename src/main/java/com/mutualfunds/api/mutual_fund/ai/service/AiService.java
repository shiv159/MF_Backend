package com.mutualfunds.api.mutual_fund.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final PortfolioContextService portfolioContextService;

    public AiService(ChatClient.Builder builder, PortfolioContextService portfolioContextService) {
        this.portfolioContextService = portfolioContextService;
        this.chatClient = builder
                .defaultSystem(
                        """
                                You are a concise financial advisor for mutual fund portfolios.

                                RESPONSE STYLE:
                                - Keep responses SHORT (3-5 sentences max for simple questions)
                                - Use bullet points for multiple items
                                - Lead with the key insight/answer
                                - Skip generic disclaimers unless specifically relevant
                                - Only elaborate if the user asks for details

                                FOCUS ON:
                                - Direct answers to questions
                                - Actionable observations
                                - Key numbers (P&L, allocation %)
                                """)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public Flux<String> streamChat(String message, String conversationId, String userId) {
        // Wrap entire operation in reactive chain to catch all exceptions
        return Mono.fromCallable(() -> {
            // Build portfolio context if userId is provided
            String portfolioContext = "";
            if (userId != null && !userId.isBlank()) {
                try {
                    UUID userUUID = UUID.fromString(userId);
                    portfolioContext = portfolioContextService.buildPortfolioContext(userUUID);
                } catch (IllegalArgumentException e) {
                    portfolioContext = "Unable to fetch portfolio data - invalid user ID.";
                }
            } else {
                portfolioContext = "No user portfolio data available. Provide general financial advice.";
            }

            // Combine portfolio context with user message
            String enrichedMessage = String.format(
                    "## Portfolio Data\n%s\n\n## User Question\n%s",
                    portfolioContext, message);

            log.info("Calling AI with enriched message for conversationId: {}", conversationId);

            // Make the AI call
            String fullResponse = this.chatClient.prompt()
                    .user(enrichedMessage)
                    .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                    .call()
                    .content();

            return fullResponse != null ? fullResponse : "I couldn't generate a response.";
        })
                .subscribeOn(Schedulers.boundedElastic()) // Run blocking call on separate thread
                .flux()
                .onErrorResume(e -> {
                    log.error("AI Service Error", e);
                    return Flux.just("Sorry, I encountered an error: " + e.getMessage());
                });
    }
}
