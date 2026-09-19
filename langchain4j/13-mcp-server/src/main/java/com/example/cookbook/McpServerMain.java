package com.example.cookbook;

public class McpServerMain {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;

        OrderMcpServer server = new OrderMcpServer(port, new OrderService());
        server.start();

        System.out.println("MCP server on http://localhost:" + port + "/mcp");
        System.out.println("Point recipe 12 at it, or any other MCP client. Ctrl+C to stop.");

        server.join();
    }
}
