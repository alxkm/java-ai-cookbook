package com.example.cookbook;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Map;

/**
 * Token, cost and latency accounting, per call, tagged by model.
 *
 * `ChatModelListener` is the hook every LangChain4j model implementation calls. The three methods
 * are the whole contract: a request went out, a response came back, or it failed. Prices belong in
 * your own code because they change, and because only you know your contract.
 */
class MetricsListener implements ChatModelListener {

    /** USD per 1M tokens. Update these when your pricing changes. */
    private record Price(double inputPerMillion, double outputPerMillion) {
    }

    private static final Map<String, Price> PRICES = Map.of(
            "gpt-4o-mini", new Price(0.15, 0.60),
            "claude-sonnet-5", new Price(3.00, 15.00));

    private static final String SAMPLE = "cookbook.timer.sample";

    private final MeterRegistry registry;

    MetricsListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        // attributes() is a per-call map shared with onResponse and onError. Use it, not a field -
        // one listener instance serves every concurrent call.
        context.attributes().put(SAMPLE, Timer.start(registry));
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        String model = context.chatResponse().modelName() == null
                ? "unknown"
                : context.chatResponse().modelName();

        stop(context.attributes(), "cookbook.chat.latency", model, "ok");

        var usage = context.chatResponse().tokenUsage();
        if (usage != null) {
            long prompt = usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
            long completion = usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();

            registry.counter("cookbook.chat.tokens", "model", model, "kind", "prompt").increment(prompt);
            registry.counter("cookbook.chat.tokens", "model", model, "kind", "completion").increment(completion);

            Price price = PRICES.get(model);
            if (price != null) {
                double cost = prompt / 1_000_000.0 * price.inputPerMillion()
                        + completion / 1_000_000.0 * price.outputPerMillion();
                registry.counter("cookbook.chat.cost.usd", "model", model).increment(cost);
            }
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        String model = context.chatRequest().modelName() == null ? "unknown" : context.chatRequest().modelName();

        stop(context.attributes(), "cookbook.chat.latency", model, "error");
        registry.counter("cookbook.chat.errors", "model", model,
                "type", context.error().getClass().getSimpleName()).increment();
    }

    private void stop(Map<Object, Object> attributes, String name, String model, String outcome) {
        if (attributes.get(SAMPLE) instanceof Timer.Sample sample) {
            sample.stop(registry.timer(name, "model", model, "outcome", outcome));
        }
    }
}
