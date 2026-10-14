package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;

import java.time.Duration;

/**
 * 19 - Retry and rate limits (LangChain4j).
 *
 * The model can retry by itself, but it will not promise the caller an upper bound on how long the
 * whole thing takes. CallBudget does, so it owns retrying here and the model's own is switched off.
 */
public final class Retry {

    /** The whole operation's limit. Models caps each request's timeout at it. */
    static final Duration DEADLINE = Duration.ofSeconds(8);

    private Retry() {
    }

    static CallBudget budget() {
        return CallBudget.builder()
                .maxAttempts(3)
                .deadline(DEADLINE)
                .initialBackoff(Duration.ofMillis(250))
                .multiplier(3)
                .retryable(Transient.classifier())
                .build();
    }

    public static void main(String[] args) {
        String question = args.length > 0
                ? String.join(" ", args)
                : "What does a 429 from an LLM provider usually mean?";

        ChatModel model = Models.chat();
        CallBudget budget = budget();

        System.out.println("> " + question);
        try {
            System.out.println(budget.call(() -> model.chat(question)));
        }
        catch (CallBudget.BudgetExhaustedException e) {
            // The branch that makes the budget worth having: a bounded, explainable failure instead
            // of a call that returns whenever the backoff curve happens to run out.
            System.out.printf("gave up after %d attempt(s) in %s: %s%n",
                    e.attempts(), e.elapsed(), e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        }
    }
}
