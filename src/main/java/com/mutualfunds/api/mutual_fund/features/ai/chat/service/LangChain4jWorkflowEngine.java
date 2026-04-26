package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowUsage;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LangChain4jWorkflowEngine {

    private final AiWorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final LangChain4jConversationMemory memory;

    private final PortfolioStateTools portfolioStateTools;
    private final FundDataTools fundDataTools;
    private final FundAnalyticsTools fundAnalyticsTools;
    private final RecommendationTools recommendationTools;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    private final Map<String, ToolMethodBinding> toolBindings = new LinkedHashMap<>();

    @PostConstruct
    void initializeToolBindings() {
        registerToolBindings(portfolioStateTools);
        registerToolBindings(fundDataTools);
        registerToolBindings(fundAnalyticsTools);
        registerToolBindings(recommendationTools);
    }

    public <T> WorkflowResponse<T> generate(WorkflowRequest<T> request) {
        List<String> candidates = new ArrayList<>();
        candidates.add(properties.getLangchain4j().getPrimaryModelProfile());
        candidates.addAll(properties.getLangchain4j().getAlternateModelProfiles());

        RuntimeException lastFailure = null;
        for (String modelProfile : candidates) {
            try {
                return executeWithModel(request, modelProfile);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("LangChain4j workflow failed for model {} route={} scope={}: {}",
                        modelProfile,
                        request.getRoute(),
                        request.getScope(),
                        ex.getMessage());
            }
        }

        throw lastFailure == null ? new IllegalStateException("No LangChain4j model response was produced") : lastFailure;
    }

    private <T> WorkflowResponse<T> executeWithModel(WorkflowRequest<T> request, String modelProfile) {
        long startedAt = System.currentTimeMillis();
        ToolExecutionContextHolder.setUserId(request.getExecutionUserId());
        try {
            ChatModel model = OpenAiChatModel.builder()
                    .baseUrl(properties.getLangchain4j().getBaseUrl())
                    .apiKey(apiKey)
                    .modelName(modelProfile)
                    .temperature(properties.getLangchain4j().getTemperature())
                    .timeout(Duration.ofSeconds(60))
                    .logRequests(properties.getLangchain4j().isLogRequests())
                    .logResponses(properties.getLangchain4j().isLogResponses())
                    .build();

            List<ToolSpecification> toolSpecifications = resolveTools(request.getSelectedTools());
            String memoryKey = memoryKey(request);
            List<ChatMessage> history = memoryKey == null ? List.of() : memory.snapshot(memoryKey);

            log.info("lc4j_tools_attached route={} scope={} selectedTools={} detailLevel={} attachedCount={}",
                    request.getRoute(),
                    request.getScope(),
                    request.getSelectedTools(),
                    request.getDetailLevel(),
                    toolSpecifications.size());

            List<ChatMessage> conversation = new ArrayList<>();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                conversation.add(SystemMessage.from(request.getSystemPrompt()));
            }
            conversation.addAll(history);
            List<ChatMessage> inputMessages = buildInputMessages(request);
            conversation.addAll(inputMessages);

            List<ChatMessage> turnMessages = new ArrayList<>(inputMessages);
            List<String> executedTools = new ArrayList<>();
            TokenUsage totalUsage = null;

            for (int iteration = 0; iteration < properties.getMaxToolLoopIterations(); iteration++) {
                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(conversation)
                        .toolSpecifications(toolSpecifications)
                        .build();

                ChatResponse response = model.chat(chatRequest);
                totalUsage = totalUsage == null ? response.tokenUsage() : TokenUsage.sum(totalUsage, response.tokenUsage());
                AiMessage aiMessage = response.aiMessage();
                conversation.add(aiMessage);
                turnMessages.add(aiMessage);

                if (aiMessage.hasToolExecutionRequests()) {
                    log.info("lc4j_tool_request_emitted route={} scope={} toolRequests={}",
                            request.getRoute(),
                            request.getScope(),
                            aiMessage.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList());
                    List<ToolExecutionResultMessage> toolResults = executeToolRequests(aiMessage.toolExecutionRequests(), executedTools);
                    conversation.addAll(toolResults);
                    turnMessages.addAll(toolResults);
                    log.info("lc4j_tool_result_appended route={} scope={} appendedCount={}",
                            request.getRoute(),
                            request.getScope(),
                            toolResults.size());
                    continue;
                }

                String rawContent = stripCodeFence(aiMessage.text());
                T content = parseContent(rawContent, request.getOutputType());
                if (memoryKey != null) {
                    memory.append(memoryKey, turnMessages);
                }

                long latencyMs = System.currentTimeMillis() - startedAt;
                log.info("lc4j_workflow route={} scope={} modelProfile={} toolCalls={} latencyMs={} inputTokens={} outputTokens={}",
                        request.getRoute(),
                        request.getScope(),
                        modelProfile,
                        executedTools.size(),
                        latencyMs,
                        totalUsage == null ? null : totalUsage.inputTokenCount(),
                        totalUsage == null ? null : totalUsage.outputTokenCount());
                log.info("lc4j_final_tool_count route={} scope={} toolCallCount={} executedTools={}",
                        request.getRoute(),
                        request.getScope(),
                        executedTools.size(),
                        executedTools);

                maybeLogZeroToolWarning(request, executedTools);

                return WorkflowResponse.<T>builder()
                        .content(content)
                        .rawContent(rawContent)
                        .modelProfileUsed(modelProfile)
                        .fallbackUsed(false)
                        .usage(toUsage(totalUsage))
                        .executedTools(List.copyOf(executedTools))
                        .latencyMs(latencyMs)
                        .build();
            }

            throw new IllegalStateException("LangChain4j tool loop exceeded max iterations");
        } finally {
            ToolExecutionContextHolder.clear();
        }
    }

    private List<ChatMessage> buildInputMessages(WorkflowRequest<?> request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            return request.getMessages();
        }

        String promptText = request.getUserQuestion() == null ? "" : request.getUserQuestion().trim();
        if (request.getSeedContext() != null && !request.getSeedContext().isEmpty()) {
            try {
                String seed = objectMapper.writeValueAsString(request.getSeedContext());
                promptText = promptText.isBlank()
                        ? "Seed context: " + seed
                        : promptText + "\n\nSeed context: " + seed;
            } catch (Exception ex) {
                log.debug("Unable to serialize seed context: {}", ex.getMessage());
            }
        }
        if (promptText.isBlank()) {
            promptText = "Provide the requested structured output.";
        }
        return List.of(UserMessage.from(promptText));
    }

    private void maybeLogZeroToolWarning(WorkflowRequest<?> request, List<String> executedTools) {
        boolean toolEnabled = request.getSelectedTools() != null && !request.getSelectedTools().isEmpty();
        boolean advancedRoute = request.getRoute() == WorkflowRoute.LC4J_SCENARIO_ANALYSIS
                || request.getRoute() == WorkflowRoute.LC4J_REBALANCE_CRITIQUE
                || request.getRoute() == WorkflowRoute.LC4J_RECOMMENDATION_SYNTHESIS;
        if (toolEnabled && advancedRoute && executedTools.isEmpty()) {
            log.warn("lc4j_zero_tool_warning route={} scope={} selectedTools={} reason=no_tool_calls_on_tool_enabled_advanced_route",
                    request.getRoute(),
                    request.getScope(),
                    request.getSelectedTools());
        }
    }

    private List<ToolExecutionResultMessage> executeToolRequests(List<ToolExecutionRequest> requests, List<String> executedTools) {
        List<ToolExecutionResultMessage> results = new ArrayList<>();
        for (ToolExecutionRequest request : requests) {
            executedTools.add(request.name());
            String response;
            try {
                response = executeToolRequest(request);
                log.info("lc4j_tool_executed toolName={} status=OK", request.name());
            } catch (Exception ex) {
                log.warn("lc4j_tool_executed toolName={} status=ERROR message={}", request.name(), ex.getMessage());
                response = objectMapper.createObjectNode()
                        .put("status", "ERROR")
                        .put("toolName", request.name())
                        .put("message", ex.getMessage() == null ? "Tool execution failed" : ex.getMessage())
                        .toString();
            }
            results.add(ToolExecutionResultMessage.from(request, response));
        }
        return results;
    }

    private String executeToolRequest(ToolExecutionRequest request) {
        ToolMethodBinding binding = toolBindings.get(request.name());
        if (binding == null) {
            throw new IllegalArgumentException("Unknown tool: " + request.name());
        }

        JsonNode arguments;
        try {
            arguments = request.arguments() == null || request.arguments().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(request.arguments());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid tool arguments for " + request.name(), ex);
        }

        Object[] invocationArguments = buildInvocationArguments(binding.method(), arguments);
        Object result;
        try {
            result = binding.method().invoke(binding.target(), invocationArguments);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Tool invocation failed for " + request.name(), ex);
        }

        if (result == null) {
            return objectMapper.createObjectNode().put("status", "OK").toString();
        }
        if (result instanceof String stringResult) {
            return stringResult;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize tool result for " + request.name(), ex);
        }
    }

    private Object[] buildInvocationArguments(Method method, JsonNode arguments) {
        Parameter[] parameters = method.getParameters();
        Object[] invocationArguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String parameterName = resolveParameterName(parameter);
            JsonNode valueNode = arguments.path(parameterName);
            if (Optional.class.equals(parameter.getType())) {
                JavaType optionalType = objectMapper.getTypeFactory().constructType(parameter.getParameterizedType());
                JavaType innerType = optionalType.containedType(0);
                invocationArguments[i] = valueNode.isMissingNode() || valueNode.isNull()
                        ? Optional.empty()
                        : Optional.ofNullable(objectMapper.convertValue(valueNode, innerType));
                continue;
            }
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                invocationArguments[i] = null;
                continue;
            }
            invocationArguments[i] = objectMapper.convertValue(valueNode,
                    objectMapper.getTypeFactory().constructType(parameter.getParameterizedType()));
        }
        return invocationArguments;
    }

    private String resolveParameterName(Parameter parameter) {
        P annotation = parameter.getAnnotation(P.class);
        if (annotation != null && annotation.name() != null && !annotation.name().isBlank()) {
            return annotation.name();
        }
        return parameter.getName();
    }

    private List<ToolSpecification> resolveTools(List<String> selectedTools) {
        if (selectedTools == null || selectedTools.isEmpty()) {
            return toolBindings.values().stream().map(ToolMethodBinding::specification).toList();
        }
        return selectedTools.stream()
                .map(toolBindings::get)
                .filter(Objects::nonNull)
                .map(ToolMethodBinding::specification)
                .toList();
    }

    private void registerToolBindings(Object toolBean) {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(toolBean);
        for (ToolSpecification specification : specifications) {
            Method method = findMethod(toolBean, specification.name());
            toolBindings.put(specification.name(), new ToolMethodBinding(specification, method, toolBean));
        }
    }

    private Method findMethod(Object toolBean, String toolName) {
        return List.of(toolBean.getClass().getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> ToolSpecifications.toolSpecificationFrom(method).name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tool method found for " + toolName));
    }

    private <T> T parseContent(String rawContent, Class<T> outputType) {
        if (outputType == null || outputType == String.class) {
            return outputType == null ? null : outputType.cast(rawContent);
        }
        try {
            return objectMapper.readValue(rawContent, outputType);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to parse structured output", ex);
        }
    }

    private WorkflowUsage toUsage(TokenUsage usage) {
        if (usage == null) {
            return WorkflowUsage.builder().build();
        }
        return WorkflowUsage.builder()
                .inputTokens(usage.inputTokenCount())
                .outputTokens(usage.outputTokenCount())
                .totalTokens(usage.totalTokenCount())
                .build();
    }

    private String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        return trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
    }

    private String memoryKey(WorkflowRequest<?> request) {
        UUID conversationId = request.getConversationId();
        if (conversationId == null) {
            return null;
        }
        String scope = request.getScope() == null || request.getScope().isBlank()
                ? "default"
                : request.getScope().toLowerCase(Locale.ROOT);
        return conversationId + "::" + scope;
    }

    private record ToolMethodBinding(ToolSpecification specification, Method method, Object target) {
    }
}