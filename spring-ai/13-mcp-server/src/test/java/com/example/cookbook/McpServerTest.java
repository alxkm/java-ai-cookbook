package com.example.cookbook;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the server and talks to it with a real MCP client over streamable HTTP. No model and no API key are
 * involved - an MCP server is a tool endpoint, and that is exactly what gets asserted here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerTest {

    @LocalServerPort
    int port;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport
                        .builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build())
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void publishesTheOrderTools() {
        McpSchema.ListToolsResult tools = client.listTools();

        assertThat(tools.tools()).extracting(McpSchema.Tool::name)
                .contains("orderStatus", "estimatedDelivery", "today");

        McpSchema.Tool orderStatus = tools.tools().stream()
                .filter(tool -> tool.name().equals("orderStatus"))
                .findFirst()
                .orElseThrow();

        assertThat(orderStatus.description()).contains("delivery status");
    }

    @Test
    void callsAToolOverTheProtocol() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("orderStatus", Map.of("orderId", "A-1001")));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().get(0).toString()).contains("shipped");
    }
}
