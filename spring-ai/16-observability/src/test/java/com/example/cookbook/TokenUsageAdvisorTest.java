package com.example.cookbook;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokenUsageAdvisorTest {

    /** Reports usage the way a real provider does, so the metrics path is exercised offline. */
    static class UsageReportingModel implements ChatModel {

        private final String reportedModel;
        private final boolean reportsUsage;

        UsageReportingModel(String reportedModel, boolean reportsUsage) {
            this.reportedModel = reportedModel;
            this.reportsUsage = reportsUsage;
        }

        UsageReportingModel() {
            this("gpt-4o-mini", true);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model(reportedModel);
            if (reportsUsage) {
                metadata.usage(new DefaultUsage(1000, 500));
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))), metadata.build());
        }
    }

    private static MeterRegistry callTwice(ChatModel model) {
        MeterRegistry registry = new SimpleMeterRegistry();
        ChatClient client = ChatClient.builder(model).defaultAdvisors(new TokenUsageAdvisor(registry)).build();
        client.prompt().user("first").call().content();
        client.prompt().user("second").call().content();
        return registry;
    }

    @Test
    void countsTokensAndCostPerModel() {
        MeterRegistry registry = new SimpleMeterRegistry();

        ChatClient client = ChatClient.builder(new UsageReportingModel())
                .defaultAdvisors(new TokenUsageAdvisor(registry))
                .build();

        client.prompt().user("first").call().content();
        client.prompt().user("second").call().content();

        assertThat(registry.counter("cookbook.chat.tokens", "model", "gpt-4o-mini", "kind", "prompt").count())
                .isEqualTo(2000);
        assertThat(registry.counter("cookbook.chat.tokens", "model", "gpt-4o-mini", "kind", "completion").count())
                .isEqualTo(1000);

        // 2 x (1000 input at 0.15/M + 500 output at 0.60/M) = 0.0009
        assertThat(registry.counter("cookbook.chat.cost.usd", "model", "gpt-4o-mini").count())
                .isCloseTo(0.0009, within(1e-9));

        assertThat(registry.timer("cookbook.chat.latency", "model", "gpt-4o-mini").count()).isEqualTo(2);
    }
    @Test
    void aDatedSnapshotIsPricedAsTheModelItBelongsTo() {
        // What OpenAI actually reports for a request to gpt-4o-mini. The old exact lookup missed it and
        // recorded no cost at all, while this file's other test - reporting the bare name - passed.
        MeterRegistry registry = callTwice(new UsageReportingModel("gpt-4o-mini-2024-07-18", true));

        assertThat(registry.counter("cookbook.chat.cost.usd", "model", "gpt-4o-mini-2024-07-18").count())
                .isCloseTo(0.0009, within(1e-9));
    }

    @Test
    void theLongestMatchingNameWins() {
        var prices = java.util.Map.of(
                "gpt-4o", new TokenUsageAdvisor.Price(2.50, 10.00),
                "gpt-4o-mini", new TokenUsageAdvisor.Price(0.15, 0.60));

        // gpt-4o-mini starts with "gpt-4o-", so a first-match lookup could charge the mini model at
        // the full model's price - about seventeen times too much.
        assertThat(TokenUsageAdvisor.priceFor(prices, "gpt-4o-mini-2024-07-18").inputPerMillion()).isEqualTo(0.15);
        assertThat(TokenUsageAdvisor.priceFor(prices, "gpt-4o-2024-08-06").inputPerMillion()).isEqualTo(2.50);
        assertThat(TokenUsageAdvisor.priceFor(prices, "gpt-4omni")).isNull();
    }

    @Test
    void aModelWithNoPriceIsCountedNotIgnored() {
        MeterRegistry registry = callTwice(new UsageReportingModel("llama3.2", true));

        assertThat(registry.counter("cookbook.chat.cost.unpriced", "model", "llama3.2").count()).isEqualTo(2);
        assertThat(registry.find("cookbook.chat.cost.usd").counter()).isNull();
    }

    @Test
    void aResponseWithoutUsageIsCountedNotIgnored() {
        MeterRegistry registry = callTwice(new UsageReportingModel("gpt-4o-mini", false));

        assertThat(registry.counter("cookbook.chat.usage.unreported", "model", "gpt-4o-mini").count()).isEqualTo(2);
    }
}
