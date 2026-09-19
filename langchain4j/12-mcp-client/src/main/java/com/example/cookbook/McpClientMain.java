package com.example.cookbook;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;

import java.time.Duration;

public class McpClientMain {

    public static void main(String[] args) throws Exception {
        String serverUrl = Models.envOr("MCP_SERVER_URL", "http://localhost:8081/mcp");

        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url(serverUrl)
                .timeout(Duration.ofSeconds(30))
                .build();

        try (McpClient mcpClient = new DefaultMcpClient.Builder()
                .transport(transport)
                .clientName("java-ai-cookbook")
                .build()) {

            System.out.println("tools discovered over MCP: "
                    + mcpClient.listTools().stream().map(tool -> tool.name()).toList());
            System.out.println();

            McpAssistant assistant = new McpAssistant(
                    Models.chat(),
                    McpToolProvider.builder().mcpClients(mcpClient).build());

            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Where is order A-1002 and when will it arrive?";

            System.out.println("> " + question);
            System.out.println(assistant.ask(question));
        }
    }
}
