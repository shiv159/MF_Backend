package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jConversationMemoryTest {

    @Test
    void shouldKeepOnlyConfiguredWindow() {
        AiWorkflowProperties properties = new AiWorkflowProperties();
        properties.setMemoryWindowMessages(2);
        properties.setMemoryWindowTokens(1000);
        LangChain4jConversationMemory memory = new LangChain4jConversationMemory(properties, new TiktokenTokenCounter());

        memory.append("conversation", List.of(
                UserMessage.from("first"),
                UserMessage.from("second"),
                UserMessage.from("third")));

        assertThat(memory.history("conversation"))
                .hasSize(2)
                .extracting(message -> ((UserMessage) message).singleText())
                .containsExactly("second", "third");
    }

    @Test
    void shouldEvictOldMessagesWhenTokenWindowExceeded() {
        AiWorkflowProperties properties = new AiWorkflowProperties();
        properties.setMemoryWindowMessages(10);
        properties.setMemoryWindowTokens(8);
        LangChain4jConversationMemory memory = new LangChain4jConversationMemory(properties, new TiktokenTokenCounter());

        memory.append("conversation", List.of(
                UserMessage.from("12345678"),
                UserMessage.from("abcdefgh"),
                UserMessage.from("ijklmnop")));

        assertThat(memory.history("conversation"))
                .extracting(message -> ((UserMessage) message).singleText())
                .containsExactly("ijklmnop");
    }
}