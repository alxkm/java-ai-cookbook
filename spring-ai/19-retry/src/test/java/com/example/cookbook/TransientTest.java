package com.example.cookbook;

import com.openai.core.http.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exceptions here are the real ones the providers' SDKs throw. Spring AI 2.0 builds its OpenAI
 * and Anthropic models on those SDKs and passes their exceptions through untouched, so a classifier
 * tested only against Spring's TransientAiException said nothing about what an OpenAI 429 does.
 */
class TransientTest {

    private static com.openai.errors.RateLimitException openAiRateLimit() {
        return com.openai.errors.RateLimitException.builder().headers(Headers.builder().build()).build();
    }

    private static com.openai.errors.BadRequestException openAiBadRequest() {
        return com.openai.errors.BadRequestException.builder().headers(Headers.builder().build()).build();
    }

    private static com.anthropic.errors.RateLimitException anthropicRateLimit() {
        return com.anthropic.errors.RateLimitException.builder()
                .headers(com.anthropic.core.http.Headers.builder().build())
                .body(com.anthropic.core.JsonValue.from(java.util.Map.of()))
                .build();
    }

    private static com.anthropic.errors.UnauthorizedException anthropicUnauthorized() {
        return com.anthropic.errors.UnauthorizedException.builder()
                .headers(com.anthropic.core.http.Headers.builder().build())
                .body(com.anthropic.core.JsonValue.from(java.util.Map.of()))
                .build();
    }

    @Test
    void anOpenAiRateLimitIsWorthRepeating() {
        assertThat(openAiRateLimit().statusCode()).isEqualTo(429);
        assertThat(Transient.isTransient(openAiRateLimit())).isTrue();
    }

    @Test
    void anOpenAiBadRequestIsNot() {
        assertThat(Transient.isTransient(openAiBadRequest())).isFalse();
    }

    @Test
    void anAnthropicRateLimitIsWorthRepeatingAndARejectedKeyIsNot() {
        assertThat(Transient.isTransient(anthropicRateLimit())).isTrue();
        assertThat(Transient.isTransient(anthropicUnauthorized())).isFalse();
    }

    @Test
    void aTransportFailureFromEitherSdkIsWorthRepeating() {
        assertThat(Transient.isTransient(new com.openai.errors.OpenAIIoException("connection reset"))).isTrue();
        assertThat(Transient.isTransient(new com.anthropic.errors.AnthropicIoException("connection reset"))).isTrue();
    }

    @Test
    void theSpringTypesStillCountForTheModelsThatUseThem() {
        // Ollama in Spring AI 2.0 still goes through RestClient and Spring AI's own retry types.
        assertThat(Transient.isTransient(new TransientAiException("503"))).isTrue();
        assertThat(Transient.isTransient(new NonTransientAiException("400"))).isFalse();
    }

    @Test
    void aWrappedSdkErrorIsStillRecognised() {
        assertThat(Transient.isTransient(new RuntimeException(openAiRateLimit()))).isTrue();
    }

    @Test
    void theBudgetRetriesARealOpenAiRateLimit() {
        AtomicInteger calls = new AtomicInteger();
        CallBudget budget = CallBudget.builder()
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(10))
                .deadline(Duration.ofSeconds(30))
                .jitter(0)
                .random(new Random(1))
                .sleeper(duration -> { })
                .retryable(Transient.classifier())
                .build();

        // The end-to-end statement of the fix: before the classifier knew the SDK types, the first
        // 429 here was rethrown as permanent and the budget never retried at all.
        String answer = budget.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw openAiRateLimit();
            }
            return "ok";
        });

        assertThat(answer).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }
}
