package com.mutualfunds.api.mutual_fund.ai.controller;

import com.mutualfunds.api.mutual_fund.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

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
}
