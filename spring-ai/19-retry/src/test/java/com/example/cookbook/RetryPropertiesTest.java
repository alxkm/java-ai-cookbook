package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CallBudget owns retrying in this recipe, so every retry layer underneath it has to be off - and in
 * Spring AI 2.0 there are three, configured in three places. Spring ignores a property it does not
 * recognise, so a key that is misspelled or that moved between versions leaves a layer quietly on:
 * every attempt the budget makes becomes several, and the deadline stops meaning anything.
 */
@SpringBootTest
@ActiveProfiles("test")
class RetryPropertiesTest {

    @Autowired
    Environment environment;

    @Autowired
    RetryTemplate retryTemplate;

    @Test
    void everyRetryLayerUnderTheBudgetIsOff() {
        // Ollama, through Spring AI's RetryTemplate.
        assertThat(environment.getProperty("spring.ai.retry.max-attempts", Integer.class)).isZero();
        // OpenAI and Anthropic, through their SDKs, which ignore spring.ai.retry.
        assertThat(environment.getProperty("spring.ai.openai.chat.max-retries", Integer.class)).isZero();
        assertThat(environment.getProperty("spring.ai.anthropic.max-retries", Integer.class)).isZero();
    }

    @Test
    void noSingleAttemptCanOutlastTheBudget() {
        // The budget checks the clock between attempts. It cannot interrupt one that is running, so
        // the per-attempt timeout is what actually bounds the first call - and the SDK default is 60s.
        assertThat(environment.getProperty("spring.ai.openai.chat.timeout", Duration.class))
                .isLessThanOrEqualTo(RetryApplication.DEADLINE);
        assertThat(environment.getProperty("spring.ai.anthropic.timeout", Duration.class))
                .isLessThanOrEqualTo(RetryApplication.DEADLINE);
    }

    @Test
    void maxAttemptsCountsRetriesSoZeroMeansExactlyOneCall() {
        AtomicInteger calls = new AtomicInteger();

        // Spring AI hands max-attempts to RetryPolicy.maxRetries. Read as attempts, 0 would mean no
        // call at all and the default of 10 would mean ten; it means one call, and eleven.
        assertThatThrownBy(() -> retryTemplate.execute(() -> {
            calls.incrementAndGet();
            throw new TransientAiException("503 unavailable");
        })).isNotNull();

        assertThat(calls).hasValue(1);
    }
}
