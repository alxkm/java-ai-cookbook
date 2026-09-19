package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupportDeskTest {

    /** First call is the routing decision, second call is the worker. */
    static class TwoStepModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();
        private final String routingJson;

        TwoStepModel(String routingJson) {
            this.routingJson = routingJson;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            requests.add(request);
            String reply = requests.size() == 1 ? routingJson : "handled";
            return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
        }
    }

    @Test
    void routesBillingMessagesToTheBillingDesk() {
        TwoStepModel model = new TwoStepModel("""
                {"desk":"BILLING","reason":"The customer mentions a duplicate charge."}
                """);

        SupportDesk.Handled handled = new SupportDesk(model)
                .handle("I was charged twice for the same invoice.");

        assertThat(handled.desk()).isEqualTo(SupportDesk.Desk.BILLING);
        assertThat(handled.reason()).contains("duplicate charge");
        assertThat(handled.answer()).isEqualTo("handled");

        // The worker gets its own system prompt, not the router's.
        String workerMessages = model.requests.get(1).messages().toString();
        assertThat(workerMessages).contains("billing desk");
        assertThat(workerMessages).doesNotContain("You route incoming support messages");
    }

    @Test
    void routesOrderMessagesToTheOrdersDesk() {
        TwoStepModel model = new TwoStepModel("""
                {"desk":"ORDERS","reason":"The customer asks about a delivery."}
                """);

        SupportDesk.Handled handled = new SupportDesk(model).handle("Where is my order A-1002?");

        assertThat(handled.desk()).isEqualTo(SupportDesk.Desk.ORDERS);
        assertThat(model.requests.get(1).messages().toString()).contains("orders desk");
        // Only the orders desk advertises tools.
        assertThat(model.requests.get(1).toolSpecifications()).isNotEmpty();
        assertThat(model.requests.get(0).toolSpecifications()).isNullOrEmpty();
    }
}
