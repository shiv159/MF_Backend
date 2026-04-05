package com.mutualfunds.api.mutual_fund.shared.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ModelRouterConfig {

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.model.router:google/gemini-2.0-flash-001}")
    private String routerModel;

    @Value("${ai.model.synthesis:anthropic/claude-sonnet-4-20250514}")
    private String synthesisModel;

    @Value("${ai.model.quick:stepfun/step-3.5-flash:free}")
    private String quickModel;

    @Bean(name = "routerChatClient")
    public ChatClient routerChatClient() {
        return buildClient(routerModel);
    }

    @Bean(name = "synthesisChatClient")
    @Primary
    public ChatClient synthesisChatClient() {
        return buildClient(synthesisModel);
    }

    @Bean(name = "quickChatClient")
    public ChatClient quickChatClient() {
        return buildClient(quickModel);
    }

    private ChatClient buildClient(String modelName) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
