package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

/**
 * An assistant whose tools come from somewhere else entirely.
 *
 * The point of MCP is that this class does not know what the tools are, who wrote them, or what
 * process they run in. It gets a list of callbacks and hands them to the model, exactly as if they
 * were local @Tool methods.
 */
class McpAssistant {

    private final ChatClient chatClient;
    private final List<ToolCallback> tools;

    McpAssistant(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = builder
                .defaultSystem("You are a support agent. Use the available tools instead of guessing.")
                .build();
        this.tools = List.of(toolCallbackProvider.getToolCallbacks());
    }

    List<String> toolNames() {
        return tools.stream().map(tool -> tool.getToolDefinition().name()).toList();
    }

    String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .toolCallbacks(tools)
                .call()
                .content();
    }
}
