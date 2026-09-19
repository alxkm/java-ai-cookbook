package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The interesting part to test offline is the tool contract: what the model is told the tools
 * are, and what the tools return when called with JSON arguments.
 */
class OrderToolsTest {

    @Test
    void exposesThreeToolsWithDescriptions() {
        ToolCallback[] callbacks = ToolCallbacks.from(new OrderTools());

        assertThat(Arrays.stream(callbacks).map(c -> c.getToolDefinition().name()))
                .containsExactlyInAnyOrder("orderStatus", "today", "estimatedDelivery");

        ToolCallback status = find(callbacks, "orderStatus");
        assertThat(status.getToolDefinition().description()).contains("delivery status");
        assertThat(status.getToolDefinition().inputSchema()).contains("orderId");
    }

    @Test
    void invokesToolWithJsonArguments() {
        ToolCallback status = find(ToolCallbacks.from(new OrderTools()), "orderStatus");

        assertThat(status.call("{\"orderId\":\"A-1001\"}")).contains("shipped");
        assertThat(status.call("{\"orderId\":\"A-9999\"}")).contains("unknown order");
    }

    private ToolCallback find(ToolCallback[] callbacks, String name) {
        return Arrays.stream(callbacks)
                .filter(c -> c.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
