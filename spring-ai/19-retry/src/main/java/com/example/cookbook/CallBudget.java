package com.example.cookbook;

import java.time.Duration;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A retry policy with a wall-clock budget.
 *
 * Both frameworks let you set an attempt count and a backoff curve. Neither lets you say the thing
 * a caller actually cares about: "this operation may take ten seconds and not a second more". The
 * total wait is left as an emergent property of attempts times multiplier, which nobody computes -
 * and that is how Spring AI's own defaults (10 attempts, 2s, multiplier 5, capped at 3 minutes)
 * add up to just over nineteen minutes on a single call.
 *
 * So the deadline is checked before sleeping, not after waking: a retry that could not finish
 * inside the budget is never started.
 */
final class CallBudget {

    /** Separated so tests do not spend real time asleep. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final class BudgetExhaustedException extends RuntimeException {

        private final int attempts;
        private final Duration elapsed;

        BudgetExhaustedException(String message, Throwable cause, int attempts, Duration elapsed) {
            super(message, cause);
            this.attempts = attempts;
            this.elapsed = elapsed;
        }

        int attempts() {
            return attempts;
        }

        Duration elapsed() {
            return elapsed;
        }
    }

    private final int maxAttempts;
    private final Duration deadline;
    private final Duration initialBackoff;
    private final double multiplier;
    private final double jitter;
    private final Predicate<Throwable> retryable;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final Random random;

    private CallBudget(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.deadline = builder.deadline;
        this.initialBackoff = builder.initialBackoff;
        this.multiplier = builder.multiplier;
        this.jitter = builder.jitter;
        this.retryable = builder.retryable;
        this.nanoClock = builder.nanoClock;
        this.sleeper = builder.sleeper;
        this.random = builder.random;
    }

    static Builder builder() {
        return new Builder();
    }

    <T> T call(Supplier<T> operation) {
        long startedAt = nanoClock.getAsLong();
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            }
            catch (RuntimeException e) {
                last = e;

                // A malformed request, a rejected key or a content-policy refusal fails the same way
                // every time. Retrying it burns the budget and changes nothing.
                if (!retryable.test(e)) {
                    throw e;
                }

                if (attempt == maxAttempts) {
                    break;
                }

                Duration wait = backoffFor(attempt);
                Duration remaining = remaining(startedAt);
                if (wait.compareTo(remaining) >= 0) {
                    throw new BudgetExhaustedException(
                            "giving up after " + attempt + " attempt(s): the next retry would not fit in the budget",
                            e, attempt, elapsed(startedAt));
                }

                try {
                    sleeper.sleep(wait);
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BudgetExhaustedException("interrupted while backing off", e, attempt, elapsed(startedAt));
                }
            }
        }

        throw new BudgetExhaustedException(
                "giving up after " + maxAttempts + " attempt(s)", last, maxAttempts, elapsed(startedAt));
    }

    private Duration backoffFor(int attempt) {
        double raw = initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1D);
        // Jitter is not decoration. Without it every client that saw the same 429 retries at the
        // same millisecond, and the provider gets the same spike again one backoff later.
        double spread = raw * jitter;
        long millis = Math.round(raw - spread + random.nextDouble() * 2 * spread);
        return Duration.ofMillis(Math.max(0, millis));
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(nanoClock.getAsLong() - startedAt);
    }

    private Duration remaining(long startedAt) {
        Duration left = deadline.minus(elapsed(startedAt));
        return left.isNegative() ? Duration.ZERO : left;
    }

    static final class Builder {

        private int maxAttempts = 3;
        private Duration deadline = Duration.ofSeconds(10);
        private Duration initialBackoff = Duration.ofMillis(200);
        private double multiplier = 2;
        private double jitter = 0.2;
        private Predicate<Throwable> retryable = e -> true;
        private LongSupplier nanoClock = System::nanoTime;
        private Sleeper sleeper = duration -> Thread.sleep(duration.toMillis());
        private Random random = new Random();

        Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        Builder jitter(double jitter) {
            this.jitter = jitter;
            return this;
        }

        Builder retryable(Predicate<Throwable> retryable) {
            this.retryable = retryable;
            return this;
        }

        Builder nanoClock(LongSupplier nanoClock) {
            this.nanoClock = nanoClock;
            return this;
        }

        Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        Builder random(Random random) {
            this.random = random;
            return this;
        }

        CallBudget build() {
            return new CallBudget(this);
        }
    }
}
