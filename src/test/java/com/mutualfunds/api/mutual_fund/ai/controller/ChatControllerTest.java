package com.mutualfunds.api.mutual_fund.features.ai.api;

import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatMessageRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatStreamEvent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.PortfolioAgentService;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.StarterPromptService;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private PortfolioAgentService portfolioAgentService;

    @Mock
    private StarterPromptService starterPromptService;

    @InjectMocks
    private ChatController chatController;

    @Test
    void streamMessageRejectsUnauthenticatedRequests() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .message("hello")
                .build();

        assertThrows(ResponseStatusException.class, () -> chatController.streamMessage(request, null));
    }

    @Test
    void streamMessageUsesAuthenticatedPrincipalUserId() {
        UUID authenticatedUserId = UUID.randomUUID();
        Principal principal = authenticatedPrincipal(authenticatedUserId);

        ChatMessageRequest request = ChatMessageRequest.builder()
                .message("hello")
                .conversationId("conv-1")
                .screenContext("LANDING")
                .build();

        ChatStreamEvent response = ChatStreamEvent.builder()
                .type("status")
                .conversationId("conv-1")
                .build();

        when(portfolioAgentService.streamMessage(eq(authenticatedUserId), eq(request)))
                .thenReturn(Flux.just(response));

        List<ServerSentEvent<ChatStreamEvent>> events = chatController
                .streamMessage(request, (UsernamePasswordAuthenticationToken) principal)
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo(response);
        assertThat(events.get(0).event()).isEqualTo("status");
        verify(portfolioAgentService).streamMessage(authenticatedUserId, request);
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
