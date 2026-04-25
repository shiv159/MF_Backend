package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatStreamEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SseResponseStreamer {

    private final ObjectMapper objectMapper;

    public ChatStreamEvent event(String type, UUID conversationId, UUID assistantMessageId, JsonNode payload) {
        return ChatStreamEvent.builder()
                .type(type)
                .conversationId(conversationId == null ? null : conversationId.toString())
                .assistantMessageId(assistantMessageId == null ? null : assistantMessageId.toString())
                .payload(payload)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public ChatStreamEvent toolStart(UUID conversationId, String toolName) {
        return event("tool_start", conversationId, null, objectNode("tool", toolName));
    }

    public ChatStreamEvent toolResult(UUID conversationId, String toolName, JsonNode payload) {
        return event("tool_result", conversationId, null, objectNode("tool", toolName, "result", payload));
    }

    public List<ChatStreamEvent> streamContent(UUID conversationId, UUID assistantMessageId, String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        List<ChatStreamEvent> events = new ArrayList<>();
        for (String chunk : chunkResponse(response)) {
            events.add(ChatStreamEvent.builder()
                    .type("message_delta")
                    .conversationId(conversationId.toString())
                    .assistantMessageId(assistantMessageId.toString())
                    .contentDelta(chunk)
                    .generatedAt(LocalDateTime.now())
                    .build());
        }
        return events;
    }

    public ObjectNode objectNode(Object... keyValuePairs) {
        ObjectNode node = objectMapper.createObjectNode();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = String.valueOf(keyValuePairs[i]);
            Object value = keyValuePairs[i + 1];
            if (value == null) {
                node.putNull(key);
            } else if (value instanceof String stringValue) {
                node.put(key, stringValue);
            } else if (value instanceof Integer integerValue) {
                node.put(key, integerValue);
            } else if (value instanceof Long longValue) {
                node.put(key, longValue);
            } else if (value instanceof Double doubleValue) {
                node.put(key, doubleValue);
            } else if (value instanceof Float floatValue) {
                node.put(key, floatValue);
            } else if (value instanceof Boolean booleanValue) {
                node.put(key, booleanValue);
            } else if (value instanceof JsonNode jsonNode) {
                node.set(key, jsonNode);
            } else {
                node.set(key, objectMapper.valueToTree(value));
            }
        }
        return node;
    }

    private List<String> chunkResponse(String response) {
        List<String> chunks = new ArrayList<>();
        String[] words = response.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (!current.isEmpty() && current.length() + word.length() + 1 > 48) {
                chunks.add(current + " ");
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
