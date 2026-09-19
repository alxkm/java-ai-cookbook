package com.example.cookbook;

import com.example.cookbook.OllamaLocal.Ticket;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class RetryingEntityTest {

    private static final String VALID_JSON = """
            {"title":"Checkout 500 for EU customers","component":"checkout",
             "severity":"sev-1","customerFacing":true}
            """;

    /** Answers badly the first n times, the way a small local model does. */
    private static Function<String, String> flaky(AtomicInteger calls, int badAttempts, String badAnswer) {
        return prompt -> calls.incrementAndGet() <= badAttempts ? badAnswer : VALID_JSON;
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

        Function<String, String> chatty = prompt -> {
            calls.incrementAndGet();
            return """
                    Sure! Here is the JSON you asked for:

                    ```json
                    {"title":"Checkout 500","component":"checkout","severity":"sev-1","customerFacing":true}
                    ```
                    """;
        };

        Optional<Ticket> ticket = RetryingEntity.call(chatty, "make a ticket", Ticket.class, 3);

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
    void tellsTheModelWhichFieldsAreExpected() {
        StringBuilder prompt = new StringBuilder();

        RetryingEntity.call(text -> {
            prompt.append(text);
            return VALID_JSON;
        }, "make a ticket", Ticket.class, 1);

        assertThat(prompt.toString()).contains("title", "component", "severity", "customerFacing");
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
