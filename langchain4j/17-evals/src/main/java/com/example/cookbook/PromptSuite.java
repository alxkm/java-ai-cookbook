package com.example.cookbook;

import java.util.List;
import java.util.function.Function;

/**
 * A regression suite for a prompt.
 *
 * The judge is another model call, so this is slower and less certain than a unit test - but it is
 * the only kind of test that catches "the new prompt is worse". Run it in CI on a schedule, not on
 * every commit, and treat a drop in the pass rate as a signal rather than a hard failure.
 */
class PromptSuite {

    record Case(String question, String groundTruth) {
    }

    record Result(Case testCase, String answer, Judge.Verdict verdict) {

        boolean passed() {
            return verdict.supported();
        }
    }

    private final Function<String, String> assistant;
    private final Judge judge;

    PromptSuite(Function<String, String> assistant, Judge judge) {
        this.assistant = assistant;
        this.judge = judge;
    }

    List<Result> run(List<Case> cases) {
        return cases.stream().map(this::runOne).toList();
    }

    private Result runOne(Case testCase) {
        String answer = assistant.apply(testCase.question());
        return new Result(testCase, answer,
                judge.judge(testCase.groundTruth(), testCase.question(), answer));
    }
}
