package com.mutualfunds.api.mutual_fund.ai.controller;

import com.mutualfunds.api.mutual_fund.ai.service.AiService;
import com.mutualfunds.api.mutual_fund.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
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
        if (principal == null) {
            log.warn("WebSocket chat request from unauthenticated user");
            return;
        }

        UUID userId = extractAuthenticatedUserId(principal);
        if (userId == null) {
            log.warn("WebSocket chat request denied: unable to resolve authenticated user id");
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/reply",
                    "Error: Unable to resolve authenticated user context.");
            return;
        }

        String message = request.get("message");
        if (message == null || message.isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/reply",
                    "Please enter a message.");
            return;
        }

        String conversationId = request.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        String effectiveConversationId = conversationId;
        aiService.streamChat(message, effectiveConversationId, userId)
                .subscribe(
                        content -> {
                            messagingTemplate.convertAndSendToUser(
                                    principal.getName(),
                                    "/queue/reply",
                                    content);
                        },
                        error -> {
                            log.error("Chat streaming error", error);
                            messagingTemplate.convertAndSendToUser(
                                    principal.getName(),
                                    "/queue/reply",
                                    "Error: " + error.getMessage());
                        });
    }

    /**
     * Reliable HTTP chat endpoint used as frontend fallback/primary path.
     * This avoids dependency on WebSocket principal routing for single-response chat.
     */
    @PostMapping("/api/chat/message")
    @ResponseBody
    public Mono<Map<String, String>> sendMessageHttp(@RequestBody Map<String, String> request, Principal principal) {
        if (principal == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        }

        UUID userId = extractAuthenticatedUserId(principal);
        if (userId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Unable to resolve user context"));
        }

        String message = request.getOrDefault("message", "");
        String conversationId = request.get("conversationId");

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        if (message.isBlank()) {
            return Mono.just(responsePayload("Please enter a message.", conversationId));
        }

        final String effectiveConversationId = conversationId;
        return aiService.streamChat(message, effectiveConversationId, userId)
                .next()
                .defaultIfEmpty("I couldn't generate a response right now.")
                .map(content -> responsePayload(content, effectiveConversationId))
                .onErrorResume(error -> {
                    log.error("HTTP chat error", error);
                    return Mono.just(responsePayload("Sorry, I encountered an error: " + error.getMessage(),
                            effectiveConversationId));
                });
    }

    private UUID extractAuthenticatedUserId(Principal principal) {
        if (principal instanceof Authentication authentication) {
            Object authPrincipal = authentication.getPrincipal();
            if (authPrincipal instanceof UserPrincipal userPrincipal) {
                return userPrincipal.getUserId();
            }
        }
        return null;
    }

    private Map<String, String> responsePayload(String response, String conversationId) {
        Map<String, String> body = new HashMap<>();
        body.put("response", response);
        body.put("conversationId", conversationId);
        return body;
    }
}
