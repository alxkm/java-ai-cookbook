package com.example.cookbook;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The listener contexts have public constructors, so the whole measurement path can be driven
 * offline without a provider.
 */
class MetricsListenerTest {

    private static final ChatRequest REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("hello"))
            .modelName("gpt-4o-mini")
            .build();

    @Test
    void countsTokensCostAndLatency() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsListener listener = new MetricsListener(registry);

        for (int i = 0; i < 2; i++) {
            Map<Object, Object> attributes = new HashMap<>();
            listener.onRequest(new ChatModelRequestContext(REQUEST, ModelProvider.OPEN_AI, attributes));
            listener.onResponse(new ChatModelResponseContext(
                    ChatResponse.builder()
                            .aiMessage(AiMessage.from("ok"))
                            .modelName("gpt-4o-mini")
                            .tokenUsage(new TokenUsage(1000, 500))
                            .build(),
                    REQUEST, ModelProvider.OPEN_AI, attributes));
        }

        assertThat(registry.counter("cookbook.chat.tokens", "model", "gpt-4o-mini", "kind", "prompt").count())
                .isEqualTo(2000);
        assertThat(registry.counter("cookbook.chat.tokens", "model", "gpt-4o-mini", "kind", "completion").count())
                .isEqualTo(1000);

        // 2 x (1000 input at 0.15/M + 500 output at 0.60/M) = 0.0009
        assertThat(registry.counter("cookbook.chat.cost.usd", "model", "gpt-4o-mini").count())
                .isCloseTo(0.0009, within(1e-9));

        assertThat(registry.timer("cookbook.chat.latency", "model", "gpt-4o-mini", "outcome", "ok").count())
                .isEqualTo(2);
    }

    @Test
    void countsErrorsSeparately() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsListener listener = new MetricsListener(registry);

        Map<Object, Object> attributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(REQUEST, ModelProvider.OPEN_AI, attributes));
        listener.onError(new ChatModelErrorContext(
                new IllegalStateException("rate limited"), REQUEST, ModelProvider.OPEN_AI, attributes));

        assertThat(registry.counter("cookbook.chat.errors",
                "model", "gpt-4o-mini", "type", "IllegalStateException").count()).isEqualTo(1);
        assertThat(registry.timer("cookbook.chat.latency", "model", "gpt-4o-mini", "outcome", "error").count())
                .isEqualTo(1);
    }
    /** One full request and response through the listener, as a provider would report it. */
    private static void respond(MetricsListener listener, String reportedModel, TokenUsage usage) {
        Map<Object, Object> attributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(REQUEST, ModelProvider.OPEN_AI, attributes));
        ChatResponse.Builder response = ChatResponse.builder().aiMessage(AiMessage.from("ok")).modelName(reportedModel);
        if (usage != null) {
            response.tokenUsage(usage);
        }
        listener.onResponse(new ChatModelResponseContext(response.build(), REQUEST, ModelProvider.OPEN_AI, attributes));
    }

    @Test
    void aDatedSnapshotIsPricedAsTheModelItBelongsTo() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsListener listener = new MetricsListener(registry);

        // What OpenAI actually reports for a request to gpt-4o-mini. The old exact lookup missed it and
        // recorded no cost at all, while the test above - reporting the bare name - passed.
        respond(listener, "gpt-4o-mini-2024-07-18", new TokenUsage(1000, 500));
        respond(listener, "gpt-4o-mini-2024-07-18", new TokenUsage(1000, 500));

        assertThat(registry.counter("cookbook.chat.cost.usd", "model", "gpt-4o-mini-2024-07-18").count())
                .isCloseTo(0.0009, within(1e-9));
    }

    @Test
    void theLongestMatchingNameWins() {
        var prices = Map.of(
                "gpt-4o", new MetricsListener.Price(2.50, 10.00),
                "gpt-4o-mini", new MetricsListener.Price(0.15, 0.60));

        // gpt-4o-mini starts with "gpt-4o-", so a first-match lookup could charge the mini model at
        // the full model's price - about seventeen times too much.
        assertThat(MetricsListener.priceFor(prices, "gpt-4o-mini-2024-07-18").inputPerMillion()).isEqualTo(0.15);
        assertThat(MetricsListener.priceFor(prices, "gpt-4o-2024-08-06").inputPerMillion()).isEqualTo(2.50);
        assertThat(MetricsListener.priceFor(prices, "gpt-4omni")).isNull();
    }

    @Test
    void aModelWithNoPriceIsCountedNotIgnored() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsListener listener = new MetricsListener(registry);

        respond(listener, "llama3.2", new TokenUsage(1000, 500));

        assertThat(registry.counter("cookbook.chat.cost.unpriced", "model", "llama3.2").count()).isEqualTo(1);
        assertThat(registry.find("cookbook.chat.cost.usd").counter()).isNull();
    }

    @Test
    void aResponseWithoutUsageIsCountedNotIgnored() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsListener listener = new MetricsListener(registry);

        respond(listener, "gpt-4o-mini", null);

        assertThat(registry.counter("cookbook.chat.usage.unreported", "model", "gpt-4o-mini").count()).isEqualTo(1);
        assertThat(registry.find("cookbook.chat.cost.usd").counter()).isNull();
    }
}
