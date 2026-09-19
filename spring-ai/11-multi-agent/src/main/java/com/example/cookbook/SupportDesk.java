package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Orchestrator plus workers.
 *
 * The router is a model call that returns a typed decision; the hand-off itself is ordinary Java.
 * Keeping the routing decision structured is what makes the system debuggable: you can log it,
 * assert on it, and override it, which is not true of "let the agents talk to each other".
 */
class SupportDesk {

    enum Desk {
        /** Refunds, invoices, charges. */
        BILLING,
        /** Order status, delivery, shipping. */
        ORDERS,
        /** Anything else. */
        GENERAL
    }

    record Routing(Desk desk, String reason) {
    }

    record Handled(Desk desk, String reason, String answer) {
    }

    private static final String ROUTER_PROMPT = """
            You route incoming support messages to one desk.
            BILLING handles refunds, invoices and charges.
            ORDERS handles order status, delivery and shipping.
            GENERAL handles everything else.
            Give a one sentence reason.
            """;

    private final ChatClient router;
    private final ChatClient billing;
    private final ChatClient orders;
    private final ChatClient general;

    SupportDesk(ChatClient.Builder builder) {
        this.router = builder.build().mutate().defaultSystem(ROUTER_PROMPT).build();

        this.billing = builder.build().mutate()
                .defaultSystem("""
                        You are the billing desk. Be precise about money.
                        Refunds take five working days. Invoices are sent on the first of the month.
                        Never promise a refund you cannot confirm.
                        """)
                .build();

        this.orders = builder.build().mutate()
                .defaultSystem("""
                        You are the orders desk. Use the order tools instead of guessing,
                        and always give the order id back to the customer.
                        """)
                .build();

        this.general = builder.build().mutate()
                .defaultSystem("You are the front desk. Answer briefly, and say when something is out of scope.")
                .build();
    }

    Handled handle(String message) {
        Routing routing = router.prompt().user(message).call().entity(Routing.class);

        String answer = switch (routing.desk()) {
            case BILLING -> billing.prompt().user(message).call().content();
            case ORDERS -> orders.prompt().user(message).tools(new OrderTools()).call().content();
            case GENERAL -> general.prompt().user(message).call().content();
        };

        return new Handled(routing.desk(), routing.reason(), answer);
    }
}
