package com.example.cookbook;

import com.example.cookbook.OllamaApplication.Ticket;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetryingEntityTest {

    private static final String VALID_JSON = """
            {"title":"Checkout 500 for EU customers","component":"checkout",
             "severity":"sev-1","customerFacing":true}
            """;

    /** Answers badly the first n times, the way a small local model does. */
    private static ChatClient flaky(AtomicInteger calls, int badAttempts, String badAnswer) {
        ChatModel model = (Prompt prompt) -> {
            String reply = calls.incrementAndGet() <= badAttempts ? badAnswer : VALID_JSON;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        };
        return ChatClient.builder(model).build();
    }

    @Test
    void succeedsOnTheFirstTryWhenTheModelBehaves() {
        AtomicInteger calls = new AtomicInteger();

        Optional<Ticket> ticket = RetryingEntity.call(flaky(calls, 0, ""), "make a ticket", Ticket.class, 3);

        assertThat(ticket).isPresent();
        assertThat(ticket.get().component()).isEqualTo("checkout");
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesAfterProse() {
        AtomicInteger calls = new AtomicInteger();

        Optional<Ticket> ticket = RetryingEntity.call(
                flaky(calls, 2, "Sure! I can help with that."), "make a ticket", Ticket.class, 3);

        assertThat(ticket).isPresent();
        assertThat(calls).hasValue(3);
    }

    @Test
    void salvagesJsonFromAChattyAnswerWithoutAnotherCall() {
        AtomicInteger calls = new AtomicInteger();

        ChatClient chatClient = ChatClient.builder((Prompt prompt) -> {
            calls.incrementAndGet();
            String reply = """
                    Sure! Here is the JSON you asked for:

                    ```json
                    {"title":"Checkout 500","component":"checkout","severity":"sev-1","customerFacing":true}
                    ```
                    """;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }).build();

        Optional<Ticket> ticket = RetryingEntity.call(chatClient, "make a ticket", Ticket.class, 3);

        assertThat(ticket).isPresent();
        assertThat(ticket.get().component()).isEqualTo("checkout");
        assertThat(calls).hasValue(1);
    }

    @Test
    void givesUpInsteadOfLoopingForever() {
        AtomicInteger calls = new AtomicInteger();

        Optional<Ticket> ticket = RetryingEntity.call(
                flaky(calls, 99, "no json here"), "make a ticket", Ticket.class, 3);

        assertThat(ticket).isEmpty();
        assertThat(calls).hasValue(3);
    }

    @Test
    void salvagesJsonFromAFencedOrChattyAnswer() {
        String chatty = """
                Sure! Here is the JSON you asked for:

                ```json
                {"title":"Checkout 500","component":"checkout","severity":"sev-1","customerFacing":true}
                ```

                Let me know if you need anything else.
                """;

        assertThat(RetryingEntity.extractJsonObject(chatty))
                .hasValueSatisfying(json -> assertThat(json)
                        .startsWith("{")
                        .endsWith("}")
                        .contains("\"component\":\"checkout\""));
    }

    @Test
    void handlesBracesInsideStrings() {
        String tricky = "prefix {\"title\":\"a } brace\",\"component\":\"x\"} suffix";

        assertThat(RetryingEntity.extractJsonObject(tricky))
                .hasValue("{\"title\":\"a } brace\",\"component\":\"x\"}");
    }

    @Test
    void returnsEmptyWhenThereIsNoJson() {
        assertThat(RetryingEntity.extractJsonObject("not json at all")).isEmpty();
        assertThat(RetryingEntity.extractJsonObject("{unbalanced")).isEmpty();
        assertThat(RetryingEntity.extractJsonObject(null)).isEmpty();
    }
}
