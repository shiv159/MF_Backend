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
    private final ChatClient diagnosticClient;
    private final PortfolioContextService portfolioContextService;

    public AiService(ChatClient.Builder builder, PortfolioContextService portfolioContextService) {
        this.portfolioContextService = portfolioContextService;
        // Build raw clients without mutating the shared builder's system prompt
        this.chatClient = builder
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();

        this.diagnosticClient = builder.build();
    }

    private static final String CHAT_SYSTEM_PROMPT = """
            You are a highly concise financial assistant. You are chatting with a user about their mutual fund portfolio.
            You will be provided with their Portfolio Data and their Question.

            STRICT FORMATTING RULES:
            1. EXTREME BREVITY: Your entire response MUST NOT exceed 150 words.
            2. NO EXHAUSTIVE SUMMARIES: Do NOT summarize the entire portfolio unless explicitly asked. Answer exactly what is asked and nothing more.
            3. SCANNABILITY: Use short bullet points (max 3 bullets). Use **bold text** for important numbers or fund names.
            4. TONE: Conversational, direct, and crisp. No fluff, no introductory/concluding essays.
            5. DISCLAIMERS: Never include financial disclaimers.

            If the user asks a general question like "Analyze my portfolio", provide a 3-4 sentence high-level observation highlighting only the most critical risk or strength, not a full breakdown.
            """;

    private static final String DIAGNOSTIC_SYSTEM_PROMPT = """
            You are a mutual fund portfolio analyst. You will receive structured diagnostic data
            about a user's portfolio including metrics, detected issues, and their severity.

            Your job is to generate personalized, actionable insights. Respond ONLY with valid JSON
            (no markdown, no code fences) matching this exact structure:

            {
              "summary": "2-4 sentence portfolio health overview using specific numbers",
              "suggestionMessages": {
                "ISSUE_CATEGORY": "Personalized actionable advice for this specific issue"
              },
              "strengths": ["strength 1", "strength 2", "strength 3"]
            }

            RULES:
            - summary: 2-4 sentences, under 80 words, factual with specific numbers
            - suggestionMessages: one entry per detected issue category, personalized and actionable,
              reference specific fund names/AMCs/sectors from the data
            - strengths: 2-3 genuine positives about the portfolio, be specific not generic
            - Use Indian Rupee (₹) for currency amounts
            - Be direct and conversational, not corporate
            - Do NOT include any disclaimers or warnings about seeking professional advice
            - Do NOT wrap the response in markdown code blocks
            """;

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
                    .system(DIAGNOSTIC_SYSTEM_PROMPT)
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
                    .system(CHAT_SYSTEM_PROMPT)
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
