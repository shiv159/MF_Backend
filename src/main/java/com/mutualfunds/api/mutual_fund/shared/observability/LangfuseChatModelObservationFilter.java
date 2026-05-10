package com.mutualfunds.api.mutual_fund.shared.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class LangfuseChatModelObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            return context;
        }

        chatModelObservationContext.addHighCardinalityKeyValue(
                KeyValue.of("gen_ai.prompt", String.join("\n\n", promptTexts(chatModelObservationContext))));
        chatModelObservationContext.addHighCardinalityKeyValue(
                KeyValue.of("gen_ai.completion", String.join("\n\n", completionTexts(chatModelObservationContext))));

        return chatModelObservationContext;
    }

    private List<String> promptTexts(ChatModelObservationContext context) {
        if (context.getRequest() == null || CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
            return List.of();
        }
        return context.getRequest().getInstructions().stream()
                .map(Content::getText)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> completionTexts(ChatModelObservationContext context) {
        if (context.getResponse() == null || CollectionUtils.isEmpty(context.getResponse().getResults())) {
            return List.of();
        }
        return context.getResponse().getResults().stream()
                .map(Generation::getOutput)
                .filter(output -> output != null && StringUtils.hasText(output.getText()))
                .map(Content::getText)
                .toList();
    }
}
