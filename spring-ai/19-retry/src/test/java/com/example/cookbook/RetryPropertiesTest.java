package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring ignores a property it does not recognise, so a retry block that is subtly misspelled or
 * that moved between versions costs nothing at startup and quietly leaves the defaults in place -
 * the same defaults this recipe exists to warn about. This asserts the yaml actually binds.
 */
@SpringBootTest
@ActiveProfiles("test")
class RetryPropertiesTest {

    @Autowired
    SpringAiRetryProperties retry;

    @Test
    void theYamlReplacesTheFrameworkDefaults() {
        assertThat(retry.getMaxAttempts()).isEqualTo(3);
        assertThat(retry.getBackoff().getInitialInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(retry.getBackoff().getMultiplier()).isEqualTo(3);
        assertThat(retry.getBackoff().getMaxInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(retry.isOnClientErrors()).isFalse();
    }

    @Test
    void theWholeRetryCurveFitsInSomethingAHumanWouldWaitFor() {
        // The number that matters is not any single setting but what they multiply out to. Left at
        // the framework defaults this sum is over nineteen minutes.
        long worstCaseMillis = 0;
        long interval = retry.getBackoff().getInitialInterval().toMillis();
        for (int attempt = 1; attempt < retry.getMaxAttempts(); attempt++) {
            worstCaseMillis += Math.min(interval, retry.getBackoff().getMaxInterval().toMillis());
            interval *= retry.getBackoff().getMultiplier();
        }

        assertThat(Duration.ofMillis(worstCaseMillis)).isLessThanOrEqualTo(Duration.ofSeconds(10));
    }
}
