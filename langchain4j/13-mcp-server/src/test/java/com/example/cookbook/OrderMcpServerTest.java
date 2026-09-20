package com.example.cookbook;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.InstanceOfAssertFactories;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the server and talks to it with a real MCP client over streamable HTTP. No model and no API key are
 * involved - an MCP server is a tool endpoint, and that is exactly what gets asserted here.
 */
class OrderMcpServerTest {

    private static OrderMcpServer server;
    private static McpSyncClient client;

    @BeforeAll
    static void startServerAndConnect() throws Exception {
        server = new OrderMcpServer(0, new OrderService());
        server.start();

        client = McpClient.sync(HttpClientStreamableHttpTransport
                        .builder("http://localhost:" + server.port())
                        .endpoint("/mcp")
                        .build())
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        client.initialize();
    }

    @AfterAll
    static void disconnectAndStop() throws Exception {
        if (client != null) {
            client.closeGracefully();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void publishesTheOrderTools() {
        McpSchema.ListToolsResult tools = client.listTools();

        assertThat(tools.tools()).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("orderStatus", "estimatedDelivery", "today");

        McpSchema.Tool orderStatus = tools.tools().stream()
                .filter(tool -> tool.name().equals("orderStatus"))
                .findFirst()
                .orElseThrow();

        assertThat(orderStatus.description()).contains("delivery status");
        // MCP 2.0 hands back the raw JSON Schema as a Map instead of a typed object, so the
        // required list is read out by key rather than by getter.
        assertThat(orderStatus.inputSchema())
                .extractingByKey("required", as(InstanceOfAssertFactories.list(String.class)))
                .containsExactly("orderId");
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
