package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.util.List;

public class Evals {

    static final List<PromptSuite.Case> CASES = List.of(
            new PromptSuite.Case(
                    "When can I deploy to production?",
                    "Production deployments happen on Tuesdays and Thursdays between 10:00 and 16:00 CET."),
            new PromptSuite.Case(
                    "How many approvals does a payments change need?",
                    "Changes to the payments module need two approving reviews, one from the payments team."),
            new PromptSuite.Case(
                    "How fast must the on-call engineer acknowledge a page?",
                    "The on-call engineer acknowledges a page within 15 minutes during business hours."));

    interface Handbook {

        @SystemMessage("You are the Acme engineering handbook. Answer in one sentence.")
        String answer(String question);
    }

    public static void main(String[] args) {
        ChatModel model = Models.chat();

        Handbook handbook = AiServices.create(Handbook.class, model);

        // The judge gets a clean service with no system prompt of yours on it. Sharing the
        // assistant's prompt with the judge is a classic way to get a judge that agrees with itself.
        PromptSuite suite = new PromptSuite(handbook::answer, Judge.create(model));

        List<PromptSuite.Result> results = suite.run(CASES);

        results.forEach(result -> System.out.printf("%s  %s%n    answer: %s%n    judge:  %s%n%n",
                result.passed() ? "PASS" : "FAIL",
                result.testCase().question(),
                result.answer(),
                result.verdict().reason()));

        long passed = results.stream().filter(PromptSuite.Result::passed).count();
        System.out.printf("%d/%d passed%n", passed, results.size());
    }
}
