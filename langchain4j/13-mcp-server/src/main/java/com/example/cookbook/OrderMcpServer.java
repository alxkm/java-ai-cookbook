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
 * An MCP server with no framework behind it: three tools, a JSON schema for each, and a
 * streamable HTTP transport served by an embedded Jetty.
 *
 * LangChain4j is a client library - it consumes MCP servers (recipe 12) but does not host them.
 * The official MCP Java SDK does, and this is what that looks like.
 */
public class OrderMcpServer implements AutoCloseable {

    private final Server jetty;
    private final McpSyncServer mcpServer;

    public OrderMcpServer(int port, OrderService orders) {
        // Streamable HTTP, the transport current MCP clients speak. One endpoint, not two.
        var transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .build();

        this.mcpServer = McpServer.sync(transport)
                .serverInfo("acme-orders", "1.0.0")
                .instructions("Order status and delivery estimates for the Acme store.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .toolCall(
                        tool("orderStatus",
                                "Look up the delivery status of an order by its id, for example A-1001.",
                                "orderId", "Order id in the form A-nnnn"),
                        (exchange, request) -> text(orders.orderStatus(argument(request, "orderId"))))
                .toolCall(
                        tool("estimatedDelivery",
                                "Estimate the delivery date for an order.",
                                "orderId", "Order id in the form A-nnnn"),
                        (exchange, request) -> text(orders.estimatedDelivery(argument(request, "orderId"))))
                .toolCall(
                        tool("today",
                                "Return today's date. Use it whenever the user says today or tomorrow.",
                                null, null),
                        (exchange, request) -> text(orders.today()))
                .build();

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transport), "/*");

        this.jetty = new Server(port);
        this.jetty.setHandler(context);
    }

    public void start() throws Exception {
        jetty.start();
    }

    public int port() {
        return jetty.getURI().getPort();
    }

    public void join() throws InterruptedException {
        jetty.join();
    }

    @Override
    public void close() throws Exception {
        mcpServer.closeGracefully();
        jetty.stop();
    }

    /**
     * The input schema is the tool's contract. Writing it by hand once makes it obvious what the
     * @Tool annotations in recipe 04 generate for you.
     */
    private static McpSchema.Tool tool(String name, String description, String parameter, String parameterDescription) {
        McpSchema.JsonSchema schema = parameter == null
                ? new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null)
                : new McpSchema.JsonSchema("object",
                        Map.of(parameter, Map.of("type", "string", "description", parameterDescription)),
                        List.of(parameter), false, null, null);

        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(schema)
                .build();
    }

    private static String argument(McpSchema.CallToolRequest request, String name) {
        Object value = request.arguments() == null ? null : request.arguments().get(name);
        return value == null ? "" : value.toString();
    }

    private static McpSchema.CallToolResult text(String value) {
        return McpSchema.CallToolResult.builder().addTextContent(value).build();
    }
}
