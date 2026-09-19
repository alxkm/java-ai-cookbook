package com.example.cookbook;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

import java.util.List;
import java.util.Map;

/**
 * A real MCP server, in the test JVM, on a random port.
 *
 * Stubbing the tool provider would only prove that Spring passes a list around. Standing up an
 * actual server is what proves the thing this recipe claims: tools defined in another process
 * reach the model.
 */
class TestMcpServer implements AutoCloseable {

    private final Server jetty;
    private final McpSyncServer mcpServer;

    TestMcpServer() {
        var transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .build();

        this.mcpServer = McpServer.sync(transport)
                .serverInfo("test-orders", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .toolCall(tool("orderStatus", "Look up the delivery status of an order by its id."),
                        (exchange, request) -> text("shipped"))
                .toolCall(tool("estimatedDelivery", "Estimate the delivery date for an order."),
                        (exchange, request) -> text("2026-01-01"))
                .build();

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transport), "/*");

        this.jetty = new Server(0);
        this.jetty.setHandler(context);
    }

    static TestMcpServer started() {
        TestMcpServer server = new TestMcpServer();
        try {
            server.jetty.start();
        } catch (Exception e) {
            throw new IllegalStateException("could not start the test MCP server", e);
        }
        return server;
    }

    int port() {
        return jetty.getURI().getPort();
    }

    @Override
    public void close() throws Exception {
        mcpServer.closeGracefully();
        jetty.stop();
    }

    private static McpSchema.Tool tool(String name, String description) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(new McpSchema.JsonSchema("object",
                        Map.of("orderId", Map.of("type", "string", "description", "Order id")),
                        List.of("orderId"), false, null, null))
                .build();
    }

    private static McpSchema.CallToolResult text(String value) {
        return McpSchema.CallToolResult.builder().addTextContent(value).build();
    }
}
