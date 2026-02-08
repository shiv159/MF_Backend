package com.mutualfunds.api.mutual_fund.ai.controller;

import com.mutualfunds.api.mutual_fund.ai.service.AiService;
import com.mutualfunds.api.mutual_fund.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private AiService aiService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatController chatController;

    @Test
    void sendMessageHttpRejectsUnauthenticatedRequests() {
        Map<String, String> request = Map.of("message", "hello");

        assertThrows(ResponseStatusException.class, () -> chatController.sendMessageHttp(request, null).block());
    }

    @Test
    void sendMessageHttpUsesAuthenticatedPrincipalUserId() {
        UUID authenticatedUserId = UUID.randomUUID();
        Principal principal = authenticatedPrincipal(authenticatedUserId);

        when(aiService.streamChat(eq("hello"), eq("conv-1"), eq(authenticatedUserId)))
                .thenReturn(Flux.just("secured-response"));

        Map<String, String> request = new HashMap<>();
        request.put("message", "hello");
        request.put("conversationId", "conv-1");
        request.put("userId", UUID.randomUUID().toString()); // spoofed payload value should be ignored

        Map<String, String> response = chatController.sendMessageHttp(request, principal).block();

        assertThat(response).containsEntry("response", "secured-response");
        assertThat(response).containsEntry("conversationId", "conv-1");
        verify(aiService).streamChat("hello", "conv-1", authenticatedUserId);
    }

    @Test
    void sendMessageWebSocketIgnoresPayloadUserIdAndUsesAuthenticatedContext() {
        UUID authenticatedUserId = UUID.randomUUID();
        Principal principal = authenticatedPrincipal(authenticatedUserId);

        when(aiService.streamChat(eq("ws-message"), eq("conv-2"), eq(authenticatedUserId)))
                .thenReturn(Flux.just("ws-response"));

        Map<String, String> request = new HashMap<>();
        request.put("message", "ws-message");
        request.put("conversationId", "conv-2");
        request.put("userId", UUID.randomUUID().toString()); // spoofed payload value should be ignored

        chatController.sendMessage(request, principal);

        verify(aiService).streamChat("ws-message", "conv-2", authenticatedUserId);
        verify(messagingTemplate).convertAndSendToUser(principal.getName(), "/queue/reply", "ws-response");
    }

    private Principal authenticatedPrincipal(UUID userId) {
        UserPrincipal userPrincipal = new UserPrincipal(
                "user@example.com",
                "",
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                userId);
        return new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities());
    }
}
