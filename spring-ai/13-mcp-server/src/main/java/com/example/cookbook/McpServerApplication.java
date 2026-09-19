package com.example.cookbook;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The same @Tool methods as recipe 04, published over MCP instead of being called in-process.
 * Any MCP client - recipe 12, an IDE, a desktop assistant - can now use them.
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider orderTools() {
        return ToolCallbackProvider.from(ToolCallbacks.from(new OrderTools()));
    }
}
