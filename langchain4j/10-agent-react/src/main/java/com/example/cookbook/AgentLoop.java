package com.example.cookbook;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent loop, written out instead of hidden.
 *
 * AiServices runs this loop for you (recipe 04). Doing it by hand is worth it once, because the
 * parts you actually need in production - a step limit, a trace, a stopping condition that is
 * yours - only exist when you own the loop.
 */
class AgentLoop {

    record Step(int number, String toolName, String arguments, String result) {
    }

    record Outcome(String answer, List<Step> steps, boolean stoppedOnStepLimit) {
    }

    private final ChatModel model;
    private final int maxSteps;
    private final List<ToolSpecification> specifications = new ArrayList<>();
    private final Map<String, ToolExecutor> executors = new HashMap<>();

    AgentLoop(ChatModel model, int maxSteps, Object... tools) {
        this.model = model;
        this.maxSteps = maxSteps;

        for (Object tool : tools) {
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(tool));
            for (Method method : tool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    executors.put(method.getName(), new DefaultToolExecutor(tool, method));
                }
            }
        }
    }

    Outcome run(String systemPrompt, String question) {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(question)));

        List<Step> steps = new ArrayList<>();
        ChatResponse response = model.chat(request(messages));

        while (!response.aiMessage().toolExecutionRequests().isEmpty()) {
            if (steps.size() >= maxSteps) {
                // Without this the loop is unbounded. A model that keeps asking for the same tool
                // will happily burn your budget until something else breaks.
                return new Outcome(null, steps, true);
            }

            messages.add(response.aiMessage());

            for (ToolExecutionRequest toolRequest : response.aiMessage().toolExecutionRequests()) {
                ToolExecutor executor = executors.get(toolRequest.name());
                String result = executor == null
                        ? "No such tool: " + toolRequest.name()
                        : executor.execute(toolRequest, null);

                messages.add(ToolExecutionResultMessage.from(toolRequest, result));
                steps.add(new Step(steps.size() + 1, toolRequest.name(), toolRequest.arguments(), result));
            }

            response = model.chat(request(messages));
        }

        return new Outcome(response.aiMessage().text(), steps, false);
    }

    private ChatRequest request(List<ChatMessage> messages) {
        return ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(specifications)
                .build();
    }
}
