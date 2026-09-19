package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupportDeskTest {

    /** First call is the routing decision, second call is the worker. */
    static class TwoStepModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();
        private final String routingJson;

        TwoStepModel(String routingJson) {
            this.routingJson = routingJson;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            String reply = prompts.size() == 1 ? routingJson : "handled";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    @Test
    void routesBillingMessagesToTheBillingDesk() {
        TwoStepModel model = new TwoStepModel("""
                {"desk":"BILLING","reason":"The customer mentions a duplicate charge."}
                """);

        SupportDesk.Handled handled = new SupportDesk(ChatClient.builder(model))
                .handle("I was charged twice for the same invoice.");

        assertThat(handled.desk()).isEqualTo(SupportDesk.Desk.BILLING);
        assertThat(handled.reason()).contains("duplicate charge");
        assertThat(handled.answer()).isEqualTo("handled");

        // The worker gets its own system prompt, not the router's.
        String workerSystemPrompt = model.prompts.get(1).getInstructions().get(0).getText();
        assertThat(workerSystemPrompt).contains("billing desk");
        assertThat(workerSystemPrompt).doesNotContain("You route incoming support messages");
    }

    @Test
    void routesOrderMessagesToTheOrdersDeskWithTools() {
        TwoStepModel model = new TwoStepModel("""
                {"desk":"ORDERS","reason":"The customer asks about a delivery."}
                """);

        SupportDesk.Handled handled = new SupportDesk(ChatClient.builder(model))
                .handle("Where is my order A-1002?");

        assertThat(handled.desk()).isEqualTo(SupportDesk.Desk.ORDERS);
        assertThat(model.prompts.get(1).getInstructions().get(0).getText()).contains("orders desk");
    }
}
