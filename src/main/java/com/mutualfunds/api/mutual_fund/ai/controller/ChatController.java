package com.mutualfunds.api.mutual_fund.ai.controller;

import com.mutualfunds.api.mutual_fund.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AiService aiService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Map<String, String> request, Principal principal) {
        String message = request.get("message");
        String conversationId = request.get("conversationId");
        String userIdData = request.get("userId"); // Prefer from payload if needed for logic

        String effectiveUserId = userIdData;

        // Fallback or verification using Principal
        if (principal != null) {
            log.info("WebSocket Chat request from authenticated user: {}", principal.getName());
            // Note: principal.getName() is the email based on our UserDetailService
            // If we need the UUID, we might need to look it up or trust the payload
            // For now, trusting the payload's userId for the portfolio context service
        } else {
            log.warn("WebSocket Chat request from unauthenticated user!");
        }

        log.info("Processing chat for userId: {}, conversationId: {}", effectiveUserId, conversationId);

        aiService.streamChat(message, conversationId, effectiveUserId)
                .subscribe(
                        content -> {
                            // Send to the specific user who sent the message
                            if (principal != null) {
                                messagingTemplate.convertAndSendToUser(
                                        principal.getName(),
                                        "/queue/reply",
                                        content);
                            }
                        },
                        error -> {
                            log.error("Chat streaming error", error);
                            if (principal != null) {
                                messagingTemplate.convertAndSendToUser(
                                        principal.getName(),
                                        "/queue/reply",
                                        "Error: " + error.getMessage());
                            }
                        });
    }

    /**
     * Reliable HTTP chat endpoint used as frontend fallback/primary path.
     * This avoids dependency on WebSocket principal routing for single-response chat.
     */
    @PostMapping("/api/chat/message")
    @ResponseBody
    public Mono<Map<String, String>> sendMessageHttp(@RequestBody Map<String, String> request, Principal principal) {
        String message = request.getOrDefault("message", "");
        String conversationId = request.get("conversationId");
        String userIdData = request.get("userId");

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        if (message.isBlank()) {
            return Mono.just(responsePayload("Please enter a message.", conversationId));
        }

        if (principal != null) {
            log.info("HTTP Chat request from authenticated user: {}", principal.getName());
        } else {
            log.warn("HTTP Chat request from unauthenticated user.");
        }

        final String effectiveConversationId = conversationId;
        return aiService.streamChat(message, effectiveConversationId, userIdData)
                .next()
                .defaultIfEmpty("I couldn't generate a response right now.")
                .map(content -> responsePayload(content, effectiveConversationId))
                .onErrorResume(error -> {
                    log.error("HTTP chat error", error);
                    return Mono.just(responsePayload("Sorry, I encountered an error: " + error.getMessage(),
                            effectiveConversationId));
                });
    }

    private Map<String, String> responsePayload(String response, String conversationId) {
        Map<String, String> body = new HashMap<>();
        body.put("response", response);
        body.put("conversationId", conversationId);
        return body;
    }
}
