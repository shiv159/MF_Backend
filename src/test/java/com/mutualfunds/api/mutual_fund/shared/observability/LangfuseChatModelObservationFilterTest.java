package com.mutualfunds.api.mutual_fund.shared.observability;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangfuseChatModelObservationFilterTest {

    @Test
    void addsPromptAndCompletionAsHighCardinalityAttributes() {
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .provider("openai")
                .prompt(new Prompt(List.of(
                        new SystemMessage("Use portfolio context only."),
                        new UserMessage("Analyze my portfolio."))))
                .build();
        context.setResponse(new ChatResponse(List.of(
                new Generation(new AssistantMessage("Your portfolio is diversified.")))));

        Observation.Context mapped = new LangfuseChatModelObservationFilter().map(context);

        assertThat(mapped.getHighCardinalityKeyValue("gen_ai.prompt").getValue())
                .contains("Use portfolio context only.")
                .contains("Analyze my portfolio.");
        assertThat(mapped.getHighCardinalityKeyValue("gen_ai.completion").getValue())
                .isEqualTo("Your portfolio is diversified.");
    }
}
