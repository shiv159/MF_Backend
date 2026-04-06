package com.mutualfunds.api.mutual_fund.features.ai.chat.persistence;

import com.mutualfunds.api.mutual_fund.features.ai.chat.domain.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
    List<ChatConversation> findByUser_UserIdOrderByUpdatedAtDesc(UUID userId);
}
