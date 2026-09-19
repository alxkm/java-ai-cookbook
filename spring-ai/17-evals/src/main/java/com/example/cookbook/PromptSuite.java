package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;

import java.util.List;

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

    record Result(Case testCase, String answer, boolean relevant, boolean factual) {

        boolean passed() {
            return relevant && factual;
        }
    }

    private final ChatClient assistant;
    private final Evaluator relevancy;
    private final Evaluator factCheck;

    PromptSuite(ChatClient assistant, Evaluator relevancy, Evaluator factCheck) {
        this.assistant = assistant;
        this.relevancy = relevancy;
        this.factCheck = factCheck;
    }

    List<Result> run(List<Case> cases) {
        return cases.stream().map(this::runOne).toList();
    }

    private Result runOne(Case testCase) {
        String answer = assistant.prompt().user(testCase.question()).call().content();

        // Relevancy: does the answer address the question, given the reference material?
        // Fact checking: is every claim in the answer supported by that material?
        // They fail for different reasons, which is why both are worth running.
        EvaluationRequest request = new EvaluationRequest(
                testCase.question(),
                List.of(new Document(testCase.groundTruth())),
                answer);

        return new Result(testCase, answer,
                relevancy.evaluate(request).isPass(),
                factCheck.evaluate(request).isPass());
    }
}
