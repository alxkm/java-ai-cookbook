package com.example.cookbook;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The agent loop, written out instead of hidden.
 *
 * Spring AI runs this loop for you when you call {@code .tools(...)} on a ChatClient. Doing it by
 * hand is worth it once, because the parts you actually need in production - a step limit, a trace,
 * a stopping condition that is yours - only exist when you own the loop.
 */
class AgentLoop {

    record Step(int number, String toolName, String arguments, String result) {
    }

    record Outcome(String answer, List<Step> steps, boolean stoppedOnStepLimit) {
    }

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final Object[] tools;
    private final int maxSteps;

    AgentLoop(ChatModel chatModel, ToolCallingManager toolCallingManager, int maxSteps, Object... tools) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.maxSteps = maxSteps;
        this.tools = tools;
    }

    Outcome run(String systemPrompt, String question) {
        // internalToolExecutionEnabled(false) is what hands the loop back to us: the model is told
        // about the tools, but Spring AI stops after the tool call instead of executing it.
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(tools))
                .internalToolExecutionEnabled(false)
                .build();

        List<Message> conversation = new ArrayList<>(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(question)));

        List<Step> steps = new ArrayList<>();
        Prompt prompt = new Prompt(conversation, options);
        ChatResponse response = chatModel.call(prompt);

        while (response.hasToolCalls()) {
            if (steps.size() >= maxSteps) {
                // Without this the loop is unbounded. A model that keeps asking for the same tool
                // will happily burn your budget until something else breaks.
                return new Outcome(null, steps, true);
            }

            ToolExecutionResult execution = toolCallingManager.executeToolCalls(prompt, response);
            Map<String, String> results = toolResultsByCallId(execution);

            response.getResult().getOutput().getToolCalls().forEach(call ->
                    steps.add(new Step(steps.size() + 1, call.name(), call.arguments(),
                            results.getOrDefault(call.id(), ""))));

            prompt = new Prompt(execution.conversationHistory(), options);
            response = chatModel.call(prompt);
        }

        return new Outcome(response.getResult().getOutput().getText(), steps, false);
    }

    /**
     * Tool output does not live in the message text - it is carried by ToolResponseMessage, keyed
     * by the id of the call that produced it. Reading getText() on that message gives you an empty
     * string and a trace that silently says nothing.
     */
    private Map<String, String> toolResultsByCallId(ToolExecutionResult execution) {
        List<Message> history = execution.conversationHistory();

        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof ToolResponseMessage toolResponse) {
                Map<String, String> byCallId = new LinkedHashMap<>();
                toolResponse.getResponses()
                        .forEach(toolResult -> byCallId.put(toolResult.id(), toolResult.responseData()));
                return byCallId;
            }
        }

        return Map.of();
    }
}
