package com.example.cookbook;

import java.time.LocalDate;
import java.util.Map;

/**
 * Plain domain code. Nothing here knows about MCP, models or prompts - that is the point.
 */
public class OrderService {

    private static final Map<String, String> STATUS = Map.of(
            "A-1001", "shipped",
            "A-1002", "packing",
            "A-1003", "cancelled");

    public String orderStatus(String orderId) {
        return STATUS.getOrDefault(orderId, "unknown order");
    }

    public String today() {
        return LocalDate.now().toString();
    }

    public String estimatedDelivery(String orderId) {
        return switch (orderStatus(orderId)) {
            case "shipped" -> LocalDate.now().plusDays(2).toString();
            case "packing" -> LocalDate.now().plusDays(5).toString();
            default -> "not applicable";
        };
    }
}
