package com.mutualfunds.api.mutual_fund.features.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatMessageRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatStreamEvent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.StarterPromptsResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.ChatPersistenceService;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.PortfolioAgentService;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.StarterPromptService;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.StatementAnalyzerService;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final PortfolioAgentService portfolioAgentService;
    private final StarterPromptService starterPromptService;
    private final StatementAnalyzerService statementAnalyzerService;
    private final ChatPersistenceService chatPersistenceService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> streamMessage(@Valid @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return portfolioAgentService.streamMessage(userId, request)
                .map(event -> ServerSentEvent.<ChatStreamEvent>builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    @GetMapping("/starter-prompts")
    public StarterPromptsResponse getStarterPrompts(@RequestParam(required = false) String screenContext,
            Authentication authentication) {
        return starterPromptService.getStarterPrompts(extractUserId(authentication), screenContext);
    }

    @PostMapping("/statement/analyze")
    public ResponseEntity<ObjectNode> analyzeStatement(@RequestParam("file") MultipartFile file,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(statementAnalyzerService.analyzeStatement(userId, file));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationDTO>> getConversations(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        var conversations = chatPersistenceService.getUserConversations(userId);
        return ResponseEntity.ok(conversations.stream().map(c -> new ChatConversationDTO(
                c.getConversationId().toString(),
                c.getTitle(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        )).toList());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageDTO>> getConversationMessages(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        extractUserId(authentication);
        var messages = chatPersistenceService.getConversationMessages(conversationId);
        return ResponseEntity.ok(messages.stream().map(m -> new ChatMessageDTO(
                m.getMessageId().toString(),
                m.getRole(),
                m.getContent(),
                m.getIntent(),
                m.getSources(),
                m.getActions(),
                m.getCreatedAt()
        )).toList());
    }

    public record ChatConversationDTO(String conversationId, String title,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record ChatMessageDTO(String messageId, String role, String content, String intent,
            JsonNode sources, JsonNode actions, LocalDateTime createdAt) {}

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userPrincipal.getUserId();
    }
}
