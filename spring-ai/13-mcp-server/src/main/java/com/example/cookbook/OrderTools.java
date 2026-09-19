package com.example.cookbook;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.util.Map;

/**
 * Plain Java methods. The annotations are what the model sees: the description becomes the
 * tool description, the parameter names and types become the schema.
 */
public class OrderTools {

    private static final Map<String, String> STATUS = Map.of(
            "A-1001", "shipped",
            "A-1002", "packing",
            "A-1003", "cancelled");

    @Tool(description = "Look up the delivery status of an order by its id, for example A-1001.")
    String orderStatus(@ToolParam(description = "Order id in the form A-nnnn") String orderId) {
        return STATUS.getOrDefault(orderId, "unknown order");
    }

    @Tool(description = "Return today's date. Use it whenever the user says today, tomorrow or yesterday.")
    String today() {
        return LocalDate.now().toString();
    }

    @Tool(description = "Estimate the delivery date for an order.")
    String estimatedDelivery(@ToolParam(description = "Order id in the form A-nnnn") String orderId) {
        String status = orderStatus(orderId);
        return switch (status) {
            case "shipped" -> LocalDate.now().plusDays(2).toString();
            case "packing" -> LocalDate.now().plusDays(5).toString();
            default -> "not applicable";
        };
    }
}
