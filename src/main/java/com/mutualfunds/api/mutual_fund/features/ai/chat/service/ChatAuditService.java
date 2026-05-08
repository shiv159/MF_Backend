package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatMessageRequest;
import com.mutualfunds.api.mutual_fund.shared.observability.CorrelationIdHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAuditService {

    public void recordMessage(UUID userId, ChatMessageRequest request) {
        if (request == null) {
            return;
        }
        log.info("chat_audit correlationId={} userId={} conversationId={} screenContext={} messageLength={}",
                CorrelationIdHolder.get(),
                userId,
                request.getConversationId(),
                request.getScreenContext(),
                request.getMessage() == null ? 0 : request.getMessage().length());
    }
}
