package com.example.cookbook;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The suite itself is ordinary code and deserves an ordinary test. Stubbing the judges is the
 * point: you want to know the harness reports correctly before you trust what it says about a prompt.
 */
class PromptSuiteTest {

    private static ChatClient assistantReturning(String answer) {
        ChatModel model = (Prompt prompt) ->
                new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        return ChatClient.builder(model).build();
    }

    private static Evaluator verdict(boolean pass) {
        return request -> new EvaluationResponse(pass, pass ? "yes" : "no", java.util.Map.of());
    }

    private static final List<PromptSuite.Case> CASES = List.of(
            new PromptSuite.Case("When can I deploy?", "Deployments happen on Tuesdays and Thursdays."));

    @Test
    void passesWhenBothJudgesAgree() {
        List<PromptSuite.Result> results = new PromptSuite(
                assistantReturning("Tuesdays and Thursdays."), verdict(true), verdict(true)).run(CASES);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.passed()).isTrue();
            assertThat(result.answer()).isEqualTo("Tuesdays and Thursdays.");
        });
    }

    @Test
    void failsWhenTheAnswerIsRelevantButNotSupported() {
        List<PromptSuite.Result> results = new PromptSuite(
                assistantReturning("Any day you like."), verdict(true), verdict(false)).run(CASES);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.relevant()).isTrue();
            assertThat(result.factual()).isFalse();
            assertThat(result.passed()).isFalse();
        });
    }

    @Test
    void runsEveryCase() {
        List<PromptSuite.Case> many = List.of(
                new PromptSuite.Case("a", "a"),
                new PromptSuite.Case("b", "b"),
                new PromptSuite.Case("c", "c"));

        assertThat(new PromptSuite(assistantReturning("x"), verdict(true), verdict(true)).run(many))
                .hasSize(3);
    }
}
