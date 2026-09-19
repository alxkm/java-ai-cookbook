package com.example.cookbook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The suite itself is ordinary code and deserves an ordinary test. Stubbing the judge is the point:
 * you want to know the harness reports correctly before you trust what it says about a prompt.
 */
class PromptSuiteTest {

    private static final List<PromptSuite.Case> CASES = List.of(
            new PromptSuite.Case("When can I deploy?", "Deployments happen on Tuesdays and Thursdays."));

    private static Judge verdict(boolean supported) {
        return (reference, question, answer) ->
                new Judge.Verdict(supported, supported ? "supported" : "not in the reference");
    }

    @Test
    void passesWhenTheJudgeSaysSupported() {
        List<PromptSuite.Result> results =
                new PromptSuite(question -> "Tuesdays and Thursdays.", verdict(true)).run(CASES);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.passed()).isTrue();
            assertThat(result.answer()).isEqualTo("Tuesdays and Thursdays.");
        });
    }

    @Test
    void failsWhenTheJudgeSaysUnsupported() {
        List<PromptSuite.Result> results =
                new PromptSuite(question -> "Any day you like.", verdict(false)).run(CASES);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.passed()).isFalse();
            assertThat(result.verdict().reason()).contains("not in the reference");
        });
    }

    @Test
    void judgeSeesTheQuestionTheAnswerAndTheReference() {
        StringBuilder seen = new StringBuilder();

        Judge recording = (reference, question, answer) -> {
            seen.append(reference).append('|').append(question).append('|').append(answer);
            return new Judge.Verdict(true, "ok");
        };

        new PromptSuite(question -> "Tuesdays.", recording).run(CASES);

        assertThat(seen.toString())
                .isEqualTo("Deployments happen on Tuesdays and Thursdays.|When can I deploy?|Tuesdays.");
    }

    @Test
    void runsEveryCase() {
        List<PromptSuite.Case> many = List.of(
                new PromptSuite.Case("a", "a"),
                new PromptSuite.Case("b", "b"),
                new PromptSuite.Case("c", "c"));

        assertThat(new PromptSuite(question -> "x", verdict(true)).run(many)).hasSize(3);
    }
}
