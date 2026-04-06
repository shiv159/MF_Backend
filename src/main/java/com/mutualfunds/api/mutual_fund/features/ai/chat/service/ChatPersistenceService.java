package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.domain.ChatConversation;
import com.mutualfunds.api.mutual_fund.features.ai.chat.domain.ChatMessage;
import com.mutualfunds.api.mutual_fund.features.ai.chat.persistence.ChatConversationRepository;
import com.mutualfunds.api.mutual_fund.features.ai.chat.persistence.ChatMessageRepository;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.users.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatPersistenceService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatConversation getOrCreateConversation(UUID userId, UUID conversationId) {
        if (conversationId != null) {
            Optional<ChatConversation> existing = conversationRepository.findById(conversationId);
            if (existing.isPresent() && existing.get().getUser().getUserId().equals(userId)) {
                return existing.get();
            }
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        ChatConversation conversation = ChatConversation.builder()
                .user(user)
                .title("New conversation")
                .build();
        return conversationRepository.save(conversation);
    }

    @Transactional
    public ChatMessage saveUserMessage(ChatConversation conversation, String content) {
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .role("user")
                .content(content)
                .build();
        return messageRepository.save(message);
    }

    @Transactional
    public ChatMessage saveAssistantMessage(ChatConversation conversation, String content, String intent,
                                             JsonNode toolTrace, JsonNode sources, JsonNode actions) {
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .role("assistant")
                .content(content)
                .intent(intent)
                .toolTrace(toolTrace)
                .sources(sources)
                .actions(actions)
                .build();
        ChatMessage saved = messageRepository.save(message);

        // Update conversation title from first assistant message
        if (conversation.getTitle() == null || "New conversation".equals(conversation.getTitle())) {
            String title = content.length() > 80 ? content.substring(0, 80) + "..." : content;
            conversation.setTitle(title);
            conversationRepository.save(conversation);
        }

        return saved;
    }

    public List<ChatConversation> getUserConversations(UUID userId) {
        return conversationRepository.findByUser_UserIdOrderByUpdatedAtDesc(userId);
    }

    public List<ChatMessage> getConversationMessages(UUID conversationId) {
        return messageRepository.findByConversation_ConversationIdOrderByCreatedAtAsc(conversationId);
    }
}
