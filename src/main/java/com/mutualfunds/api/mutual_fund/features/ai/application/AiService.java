package com.mutualfunds.api.mutual_fund.features.ai.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptId;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final ChatClient diagnosticClient;
    private final PortfolioContextService portfolioContextService;
    private final PromptRegistry promptRegistry;

    public AiService(ChatClient.Builder builder, PortfolioContextService portfolioContextService,
            PromptRegistry promptRegistry) {
        this.portfolioContextService = portfolioContextService;
        this.promptRegistry = promptRegistry;
        // Build raw clients without mutating the shared builder's system prompt
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(20)
                        .build()).build())
                .build();

        this.diagnosticClient = builder.build();
    }


    /**
     * Generate AI-powered diagnostic insights: summary, suggestion messages, and
     * strengths.
     * Returns raw JSON string to be parsed by the caller.
     *
     * @param diagnosticContext Text representation of portfolio metrics and
     *                          detected issues
     * @return JSON string with summary, suggestionMessages, and strengths; or empty
     *         string on failure
     */
    public String generateDiagnosticInsights(String diagnosticContext) {
        try {
            log.info("Generating AI diagnostic insights");
            String response = this.diagnosticClient.prompt()
                    .system(promptRegistry.text(PromptId.AI_DIAGNOSTIC_SYSTEM))
                    .user(diagnosticContext)
                    .call()
                    .content();
            return response != null ? response.trim() : "";
        } catch (Exception e) {
            log.error("Failed to generate AI diagnostic insights: {}", e.getMessage());
            return ""; // Fallback: caller will use template messages
        }
    }

    public Flux<String> streamChat(String message, String conversationId, UUID userId) {
        // Wrap entire operation in reactive chain to catch all exceptions
        return Mono.fromCallable(() -> {
            // Build portfolio context if userId is provided
            String portfolioContext = "";
            if (userId != null) {
                portfolioContext = portfolioContextService.buildPortfolioContext(userId);
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
                    .system(promptRegistry.text(PromptId.AI_CHAT_SYSTEM))
                    .user(enrichedMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
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
