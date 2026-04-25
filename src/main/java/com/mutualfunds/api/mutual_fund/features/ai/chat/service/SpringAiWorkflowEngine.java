package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpringAiWorkflowEngine {

    private final ChatSynthesisService chatSynthesisService;

    public ChatSynthesisService.SynthesisResult synthesize(
            ChatIntent intent,
            String conversationId,
            String screenContext,
            String userMessage,
            JsonNode toolPayload,
            List<String> warnings) {
        return chatSynthesisService.synthesize(intent, conversationId, screenContext, userMessage, toolPayload, warnings);
    }
}
