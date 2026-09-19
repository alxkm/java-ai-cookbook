package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

/**
 * Orchestrator plus workers.
 *
 * The router is a model call that returns a typed decision; the hand-off itself is ordinary Java.
 * Keeping the routing decision structured is what makes the system debuggable: you can log it,
 * assert on it, and override it, which is not true of "let the agents talk to each other".
 */
public class SupportDesk {

    public enum Desk {
        BILLING,
        ORDERS,
        GENERAL
    }

    public record Routing(Desk desk, String reason) {
    }

    public record Handled(Desk desk, String reason, String answer) {
    }

    interface Router {

        @SystemMessage("""
                You route incoming support messages to one desk.
                BILLING handles refunds, invoices and charges.
                ORDERS handles order status, delivery and shipping.
                GENERAL handles everything else.
                Give a one sentence reason.
                """)
        Routing route(String message);
    }

    interface BillingDesk {

        @SystemMessage("""
                You are the billing desk. Be precise about money.
                Refunds take five working days. Invoices are sent on the first of the month.
                Never promise a refund you cannot confirm.
                """)
        String answer(String message);
    }

    interface OrdersDesk {

        @SystemMessage("""
                You are the orders desk. Use the order tools instead of guessing,
                and always give the order id back to the customer.
                """)
        String answer(String message);
    }

    interface GeneralDesk {

        @SystemMessage("You are the front desk. Answer briefly, and say when something is out of scope.")
        String answer(String message);
    }

    private final Router router;
    private final BillingDesk billing;
    private final OrdersDesk orders;
    private final GeneralDesk general;

    public SupportDesk(ChatModel model) {
        this.router = AiServices.create(Router.class, model);
        this.billing = AiServices.create(BillingDesk.class, model);
        this.orders = AiServices.builder(OrdersDesk.class)
                .chatModel(model)
                .tools(new OrderTools())
                .build();
        this.general = AiServices.create(GeneralDesk.class, model);
    }

    public Handled handle(String message) {
        Routing routing = router.route(message);

        String answer = switch (routing.desk()) {
            case BILLING -> billing.answer(message);
            case ORDERS -> orders.answer(message);
            case GENERAL -> general.answer(message);
        };

        return new Handled(routing.desk(), routing.reason(), answer);
    }
}
