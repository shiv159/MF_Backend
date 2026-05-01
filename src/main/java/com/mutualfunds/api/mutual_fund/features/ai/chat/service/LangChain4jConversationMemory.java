package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LangChain4jConversationMemory {

    private final Map<String, Deque<ChatMessage>> windows = new ConcurrentHashMap<>();
    private final Map<String, Integer> tokenWindows = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final int maxTokens;
    private final TokenCounter tokenCounter;

    public LangChain4jConversationMemory(AiWorkflowProperties properties, TokenCounter tokenCounter) {
        this.maxMessages = Math.max(1, properties.getMemoryWindowMessages());
        this.maxTokens = Math.max(1, properties.getMemoryWindowTokens());
        this.tokenCounter = tokenCounter;
    }

    public List<ChatMessage> history(String key) {
        Deque<ChatMessage> messages = windows.get(key);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    public void append(String key, List<ChatMessage> messages) {
        if (key == null || key.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }

        Deque<ChatMessage> window = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        int totalTokens = tokenWindows.getOrDefault(key, estimateTokens(window));

        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            window.addLast(message);
            totalTokens += estimateTokens(message);

            while ((window.size() > maxMessages || totalTokens > maxTokens) && window.size() > 1) {
                ChatMessage removed = window.pollFirst();
                if (removed == null) {
                    break;
                }
                totalTokens -= estimateTokens(removed);
            }
        }

        tokenWindows.put(key, Math.max(0, totalTokens));
    }

    public List<ChatMessage> snapshot(String key) {
        return new ArrayList<>(history(key));
    }

    private int estimateTokens(Deque<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimateTokens(message);
        }
        return total;
    }

    private int estimateTokens(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        String serialized = message.toString();
        if (serialized == null || serialized.isBlank()) {
            return 1;
        }
        return tokenCounter.countTokens(serialized);
    }
}