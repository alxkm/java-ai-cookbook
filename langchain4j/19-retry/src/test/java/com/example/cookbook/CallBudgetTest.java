package com.example.cookbook;

import org.junit.jupiter.api.Test;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The clock and the sleeper are injected, so these run in milliseconds and assert on the exact
 * waits rather than on elapsed wall time. A retry test that really sleeps is a slow test that
 * still cannot tell you what it waited for.
 */
class CallBudgetTest {

    /** Records what the policy asked to sleep for and advances the fake clock by that much. */
    private static final class FakeTime implements CallBudget.Sleeper {

        private final AtomicLong nanos = new AtomicLong();
        private final List<Duration> slept = new ArrayList<>();

        @Override
        public void sleep(Duration duration) {
            slept.add(duration);
            nanos.addAndGet(duration.toNanos());
        }

        long now() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }

    private static CallBudget.Builder budget(FakeTime time) {
        return CallBudget.builder()
                .nanoClock(time::now)
                .sleeper(time)
                .jitter(0)
                .random(new Random(1))
                .retryable(Transient.classifier());
    }

    @Test
    void returnsTheFirstSuccessWithoutSleeping() {
        FakeTime time = new FakeTime();
        AtomicInteger calls = new AtomicInteger();

        String result = budget(time).build().call(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
        assertThat(time.slept).isEmpty();
    }

    @Test
    void retriesATransientFailureAndBacksOffExponentially() {
        FakeTime time = new FakeTime();
        AtomicInteger calls = new AtomicInteger();

        String result = budget(time)
                .maxAttempts(4)
                .initialBackoff(Duration.ofMillis(100))
                .multiplier(3)
                .deadline(Duration.ofSeconds(30))
                .build()
                .call(() -> {
                    if (calls.incrementAndGet() < 3) {
                        throw new RateLimitException("429 slow down");
                    }
                    return "ok";
                });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(time.slept).containsExactly(Duration.ofMillis(100), Duration.ofMillis(300));
    }

    @Test
    void doesNotRetryARequestThatCannotSucceed() {
        FakeTime time = new FakeTime();
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> budget(time).maxAttempts(5).build().call(() -> {
            calls.incrementAndGet();
            throw new AuthenticationException("401 invalid api key");
        })).isInstanceOf(AuthenticationException.class);

        assertThat(calls).hasValue(1);
        assertThat(time.slept).isEmpty();
    }

    @Test
    void stopsBeforeASleepThatWouldOverrunTheDeadline() {
        FakeTime time = new FakeTime();
        AtomicInteger calls = new AtomicInteger();

        // Attempts alone would allow five tries; the budget allows one sleep of 400ms and then the
        // 1200ms one no longer fits, so the call must end there rather than wait it out.
        assertThatThrownBy(() -> budget(time)
                .maxAttempts(5)
                .initialBackoff(Duration.ofMillis(400))
                .multiplier(3)
                .deadline(Duration.ofMillis(1000))
                .build()
                .call(() -> {
                    calls.incrementAndGet();
                    throw new RateLimitException("429 slow down");
                }))
                .isInstanceOf(CallBudget.BudgetExhaustedException.class)
                .hasMessageContaining("would not fit in the budget");

        assertThat(calls).hasValue(2);
        assertThat(time.slept).containsExactly(Duration.ofMillis(400));
    }

    @Test
    void countsTimeSpentInsideTheCallAgainstTheBudget() {
        FakeTime time = new FakeTime();

        // A slow call is just as capable of blowing the budget as a long backoff, so the deadline
        // has to cover both. Here the first attempt alone eats it.
        assertThatThrownBy(() -> budget(time)
                .maxAttempts(3)
                .initialBackoff(Duration.ofMillis(100))
                .deadline(Duration.ofMillis(500))
                .build()
                .call(() -> {
                    time.advance(Duration.ofMillis(600));
                    throw new InternalServerException("504 gateway timeout");
                }))
                .isInstanceOf(CallBudget.BudgetExhaustedException.class);

        assertThat(time.slept).isEmpty();
    }

    @Test
    void reportsWhatItSpentWhenTheAttemptsRunOut() {
        FakeTime time = new FakeTime();

        assertThatThrownBy(() -> budget(time)
                .maxAttempts(2)
                .initialBackoff(Duration.ofMillis(50))
                .deadline(Duration.ofSeconds(30))
                .build()
                .call(() -> {
                    throw new InternalServerException("503 unavailable");
                }))
                .isInstanceOfSatisfying(CallBudget.BudgetExhaustedException.class, e -> {
                    assertThat(e.attempts()).isEqualTo(2);
                    assertThat(e.elapsed()).isEqualTo(Duration.ofMillis(50));
                    assertThat(e).hasRootCauseMessage("503 unavailable");
                });
    }

    @Test
    void spreadsRetriesWithJitterSoClientsDoNotSynchronise() {
        // One generator shared across the simulated clients, which is what twenty separate JVMs
        // amount to. Seeding a fresh Random per client instead would prove nothing: sequential
        // seeds make java.util.Random return almost the same first draw, so twenty clients keyed
        // on an id would land within a few milliseconds of each other and the jitter would be
        // decorative. That is worth knowing before wiring a shard number into a seed.
        Random shared = new Random(42);
        List<Duration> waits = new ArrayList<>();

        for (int client = 0; client < 20; client++) {
            FakeTime clock = new FakeTime();
            try {
                budget(clock)
                        .random(shared)
                        .maxAttempts(2)
                        .initialBackoff(Duration.ofMillis(1000))
                        .jitter(0.5)
                        .deadline(Duration.ofSeconds(30))
                        .build()
                        .call(() -> {
                            throw new RateLimitException("429 slow down");
                        });
            }
            catch (CallBudget.BudgetExhaustedException expected) {
                waits.addAll(clock.slept);
            }
        }

        assertThat(waits).hasSize(20);
        assertThat(waits).allSatisfy(wait -> assertThat(wait)
                .isBetween(Duration.ofMillis(500), Duration.ofMillis(1500)));

        // Twenty clients told to wait "one second" must not all wake in the same millisecond and
        // hit the provider together. A stray collision after rounding is fine; clustering is not.
        assertThat(waits.stream().distinct().count()).isGreaterThan(15);
    }
    @Test
    void readsRetryabilityStraightOffTheExceptionHierarchy() {
        // The whole classifier on this side is two instanceof checks, because LangChain4j has
        // already sorted its exceptions into RetriableException and NonRetriableException. A
        // content-policy refusal is the interesting one: it arrives as an InvalidRequestException
        // subclass, so it is never retried even though it looks like a server-side rejection.
        assertThat(Transient.isTransient(new RateLimitException("429"))).isTrue();
        assertThat(Transient.isTransient(new InternalServerException("500"))).isTrue();
        assertThat(Transient.isTransient(new AuthenticationException("401"))).isFalse();
        assertThat(Transient.isTransient(new ContentFilteredException("refused"))).isFalse();

        // Nested causes count: providers wrap the real failure more often than not.
        assertThat(Transient.isTransient(new RuntimeException(new RateLimitException("429")))).isTrue();
    }
}
