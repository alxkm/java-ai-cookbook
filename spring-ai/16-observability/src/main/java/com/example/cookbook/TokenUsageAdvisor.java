package com.example.cookbook;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Token and cost accounting, per call, tagged by model.
 *
 * Spring AI already emits `gen_ai.client.operation` observations with token counts - that is what
 * a tracing backend picks up. This advisor exists for the other question, the one finance asks:
 * how much did this feature cost today, broken down by model. Prices belong in your own code
 * because they change, and because only you know your contract.
 */
class TokenUsageAdvisor implements CallAdvisor {

    /** USD per 1M tokens. Update these when your pricing changes. */
    record Price(double inputPerMillion, double outputPerMillion) {
    }

    private static final java.util.Map<String, Price> PRICES = java.util.Map.of(
            "gpt-4o-mini", new Price(0.15, 0.60),
            "claude-sonnet-5", new Price(3.00, 15.00));

    private final MeterRegistry registry;

    TokenUsageAdvisor(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Timer.Sample sample = Timer.start(registry);
        ChatClientResponse response = chain.nextCall(request);

        String model = response.chatResponse() == null || response.chatResponse().getMetadata() == null
                ? "unknown"
                : String.valueOf(response.chatResponse().getMetadata().getModel());

        sample.stop(registry.timer("cookbook.chat.latency", "model", model));

        Usage usage = response.chatResponse() == null ? null : response.chatResponse().getMetadata().getUsage();
        // Spring AI never hands back a null here: metadata without usage carries an EmptyUsage, whose
        // token counts are zero. Read as numbers, a provider that reports nothing becomes a provider
        // that costs nothing - the cost meter books $0.00 and every dashboard believes it. So the
        // placeholder is recognised for what it is and counted separately.
        if (usage == null || usage instanceof EmptyUsage) {
            registry.counter("cookbook.chat.usage.unreported", "model", model).increment();
        }
        else {
            long prompt = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            long completion = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();

            counter("cookbook.chat.tokens", model, "prompt").increment(prompt);
            counter("cookbook.chat.tokens", model, "completion").increment(completion);

            Price price = priceFor(PRICES, model);
            if (price == null) {
                registry.counter("cookbook.chat.cost.unpriced", "model", model).increment();
            }
            else {
                double cost = prompt / 1_000_000.0 * price.inputPerMillion()
                        + completion / 1_000_000.0 * price.outputPerMillion();
                registry.counter("cookbook.chat.cost.usd", "model", model).increment(cost);
            }
        }

        return response;
    }

    @Override
    public String getName() {
        return "token-usage";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * The model a response reports is not the name the request used. OpenAI answers a request for
     * gpt-4o-mini with gpt-4o-mini-2024-07-18, the snapshot that served it, so an exact lookup misses
     * and the cost meter never moves - the headline number of this recipe, silently zero. A price
     * applies to its name and to any dated snapshot of it, and the longest matching name wins, so a
     * gpt-4o price can never be charged for gpt-4o-mini.
     */
    static Price priceFor(java.util.Map<String, Price> prices, String model) {
        Price best = null;
        int bestLength = -1;
        for (var entry : prices.entrySet()) {
            String name = entry.getKey();
            boolean matches = model.equals(name) || model.startsWith(name + "-");
            if (matches && name.length() > bestLength) {
                best = entry.getValue();
                bestLength = name.length();
            }
        }
        return best;
    }

    private Counter counter(String name, String model, String kind) {
        return registry.counter(name, "model", model, "kind", kind);
    }
}
