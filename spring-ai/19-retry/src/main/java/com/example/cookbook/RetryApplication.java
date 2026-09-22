package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@SpringBootApplication
public class RetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetryApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are a terse Java assistant. Answer in at most three sentences.")
                .build();
    }

    /**
     * The budget the caller actually gets. Spring AI's own retry layer sits underneath this and is
     * configured in application.yml; this bounds the whole operation in wall-clock time, which the
     * property set cannot express.
     */
    @Bean
    CallBudget callBudget() {
        return CallBudget.builder()
                .maxAttempts(3)
                .deadline(Duration.ofSeconds(8))
                .initialBackoff(Duration.ofMillis(250))
                .multiplier(3)
                .retryable(Transient.classifier())
                .build();
    }

    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient, CallBudget budget) {
        return args -> {
            String question = args.length > 0
                    ? String.join(" ", args)
                    : "What does a 429 from an LLM provider usually mean?";

            System.out.println("> " + question);
            try {
                String answer = budget.call(() -> chatClient.prompt().user(question).call().content());
                System.out.println(answer);
            }
            catch (CallBudget.BudgetExhaustedException e) {
                // The point of a budget is that this branch exists and is fast. Without it the call
                // either succeeds or hangs for as long as the backoff curve happens to add up to.
                System.out.printf("gave up after %d attempt(s) in %s: %s%n",
                        e.attempts(), e.elapsed(), e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            }
        };
    }
}
