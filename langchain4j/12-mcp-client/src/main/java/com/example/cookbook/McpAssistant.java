package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.tool.ToolProvider;

/**
 * An assistant whose tools come from somewhere else entirely.
 *
 * The point of MCP is that this class does not know what the tools are, who wrote them, or what
 * process they run in. `ToolProvider` is the seam: MCP plugs in there, and so can anything else.
 */
class McpAssistant {

    interface Assistant {

        @SystemMessage("You are a support agent. Use the available tools instead of guessing.")
        String ask(String question);
    }

    private final Assistant assistant;

    McpAssistant(ChatModel model, ToolProvider toolProvider) {
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .toolProvider(toolProvider)
                .build();
    }

    String ask(String question) {
        return assistant.ask(question);
    }
}
