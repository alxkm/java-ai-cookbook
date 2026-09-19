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

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("ok"))),
                    ChatResponseMetadata.builder()
                            .model("gpt-4o-mini")
                            .usage(new DefaultUsage(1000, 500))
                            .build());
        }
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
}
