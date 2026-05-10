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
import com.mutualfunds.api.mutual_fund.shared.observability.LangfuseTraceService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class AiService {

    private final ChatClient chatClient;
    private final ChatClient diagnosticClient;
    private final PortfolioContextService portfolioContextService;
    private final PromptRegistry promptRegistry;
    private final StructuredOutputSupport structuredOutputSupport;
    private final AiSafetyService aiSafetyService;
    private final LangfuseTraceService langfuseTraceService;

    public AiService(ChatClient.Builder builder, PortfolioContextService portfolioContextService,
            PromptRegistry promptRegistry,
            StructuredOutputSupport structuredOutputSupport,
            AiSafetyService aiSafetyService,
            LangfuseTraceService langfuseTraceService) {
        this.portfolioContextService = portfolioContextService;
        this.promptRegistry = promptRegistry;
        this.structuredOutputSupport = structuredOutputSupport;
        this.aiSafetyService = aiSafetyService;
        this.langfuseTraceService = langfuseTraceService;
        // Build raw clients without mutating the shared builder's system prompt
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(20)
                        .build()).build())
                .build();

        this.diagnosticClient = builder.build();
    }


    public Optional<DiagnosticInsightsPayload> generateDiagnosticInsights(String diagnosticContext) {
        try {
            log.info("Generating AI diagnostic insights");
            Optional<DiagnosticInsightsPayload> response = structuredOutputSupport.generate(
                    DiagnosticInsightsPayload.class,
                    () -> this.diagnosticClient.prompt()
                            .system(promptRegistry.text(PromptId.AI_DIAGNOSTIC_SYSTEM))
                            .user(diagnosticContext)
                            .call()
                            .entity(DiagnosticInsightsPayload.class),
                    () -> this.diagnosticClient.prompt()
                            .system(promptRegistry.text(PromptId.AI_DIAGNOSTIC_SYSTEM))
                            .user(diagnosticContext)
                            .call()
                            .content(),
                    raw -> this.diagnosticClient.prompt()
                            .system("""
                                    Repair the following response into valid JSON only.
                                    Keep the exact schema:
                                    {"summary":"...","suggestionMessages":{"ISSUE_CATEGORY":"..."},"strengths":["..."]}
                                    Do not add markdown or explanations.
                                    """)
                            .user(raw)
                            .call()
                            .content(),
                    DiagnosticInsightsValidator::validate);
            return response.map(aiSafetyService::sanitizeDiagnosticPayload)
                    .flatMap(DiagnosticInsightsValidator::validate);
        } catch (Exception e) {
            log.error("Failed to generate AI diagnostic insights: {}", e.getMessage());
            return Optional.empty();
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

            String fullResponse = langfuseTraceService.traceChatTurn(
                    "ai-service-chat",
                    userId,
                    conversationId,
                    "SPRING_STANDARD_CHAT",
                    "spring-ai",
                    message,
                    () -> this.chatClient.prompt()
                            .system(promptRegistry.text(PromptId.AI_CHAT_SYSTEM))
                            .user(enrichedMessage)
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                            .call()
                            .content());

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
