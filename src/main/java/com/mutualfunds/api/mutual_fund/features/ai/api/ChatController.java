package com.mutualfunds.api.mutual_fund.features.ai.api;

import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatMessageRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatStreamEvent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.StarterPromptsResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.PortfolioAgentService;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.StarterPromptService;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final PortfolioAgentService portfolioAgentService;
    private final StarterPromptService starterPromptService;

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
        extractUserId(authentication);
        return starterPromptService.getStarterPrompts(screenContext);
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userPrincipal.getUserId();
    }
}
