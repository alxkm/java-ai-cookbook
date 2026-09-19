package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class McpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }

    /**
     * The starter connects to every server in application.yml and exposes their tools through a
     * single ToolCallbackProvider. Nothing here names a specific tool.
     */
    @Bean
    McpAssistant mcpAssistant(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
        return new McpAssistant(builder, toolCallbackProvider);
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(McpAssistant assistant) {
        return args -> {
            System.out.println("tools discovered over MCP: " + assistant.toolNames());
            System.out.println();

            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Where is order A-1002 and when will it arrive?";

            System.out.println("> " + question);
            System.out.println(assistant.ask(question));
        };
    }
}
