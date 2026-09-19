package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication
public class EvalsApplication {

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

    /**
     * The judge prompt is yours to own. Keep it narrow - a judge asked to score "quality" will
     * invent a scale; a judge asked one yes/no question is reproducible.
     */
    private static final String FACT_CHECK_PROMPT = """
            Document: {document}
            Claim: {claim}

            Is every statement in the claim supported by the document?
            Answer with exactly one word, yes or no.
            """;

    public static void main(String[] args) {
        SpringApplication.run(EvalsApplication.class, args);
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient.Builder builder) {
        return args -> {
            ChatClient assistant = builder
                    .defaultSystem("You are the Acme engineering handbook. Answer in one sentence.")
                    .build();

            // The judge gets its own ChatClient with no system prompt of yours on it. Sharing the
            // assistant's prompt with the judge is a classic way to get a judge that agrees with
            // everything.
            PromptSuite suite = new PromptSuite(
                    assistant,
                    new RelevancyEvaluator(builder.build().mutate()),
                    FactCheckingEvaluator.builder(builder.build().mutate())
                            .evaluationPrompt(FACT_CHECK_PROMPT)
                            .build());

            List<PromptSuite.Result> results = suite.run(CASES);

            results.forEach(result -> System.out.printf("%s  relevant=%-5s factual=%-5s  %s%n    %s%n%n",
                    result.passed() ? "PASS" : "FAIL",
                    result.relevant(), result.factual(),
                    result.testCase().question(),
                    result.answer()));

            long passed = results.stream().filter(PromptSuite.Result::passed).count();
            System.out.printf("%d/%d passed%n", passed, results.size());
        };
    }
}
