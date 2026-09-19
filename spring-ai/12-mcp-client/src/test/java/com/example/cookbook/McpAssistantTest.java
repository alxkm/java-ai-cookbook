package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MCP protocol itself is covered by recipe 13, which runs a real client against a real server.
 * What is worth checking here is the part this recipe owns: tools that arrive from outside are
 * advertised to the model without the application knowing their names.
 */
class McpAssistantTest {

    /** Stands in for a remote server's tools; from the assistant's side it is the same interface. */
    static class RemoteTools {

        @Tool(description = "Look up the delivery status of an order by its id.")
        String orderStatus(@ToolParam(description = "Order id") String orderId) {
            return "shipped";
        }
    }

    @Test
    void advertisesToolsItNeverDeclared() {
        AtomicReference<Prompt> captured = new AtomicReference<>();

        ChatModel stub = prompt -> {
            captured.set(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        };

        ToolCallbackProvider provider = ToolCallbackProvider.from(ToolCallbacks.from(new RemoteTools()));
        McpAssistant assistant = new McpAssistant(ChatClient.builder(stub), provider);

        assertThat(assistant.toolNames()).containsExactly("orderStatus");
        assertThat(assistant.ask("Where is A-1001?")).isEqualTo("ok");

        var options = (ToolCallingChatOptions) captured.get().getOptions();
        assertThat(options.getToolCallbacks())
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactly("orderStatus");
    }
}
