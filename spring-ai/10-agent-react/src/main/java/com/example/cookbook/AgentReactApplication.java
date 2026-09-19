package com.example.cookbook;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class AgentReactApplication {

    private static final String SYSTEM_PROMPT = """
            You are a support agent. Work in small steps.
            Use a tool whenever a fact is not already in the conversation, and never guess an order status.
            When you have enough information, answer in one short paragraph.
            """;

    public static void main(String[] args) {
        SpringApplication.run(AgentReactApplication.class, args);
    }

    @Bean
    ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatModel chatModel, ToolCallingManager toolCallingManager) {
        return args -> {
            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Compare orders A-1001 and A-1002: which one arrives first, and how many days from today?";

            AgentLoop agent = new AgentLoop(chatModel, toolCallingManager, 6, new OrderTools());
            AgentLoop.Outcome outcome = agent.run(SYSTEM_PROMPT, question);

            System.out.println("> " + question);
            System.out.println();

            outcome.steps().forEach(step ->
                    System.out.printf("  step %d: %s%s -> %s%n",
                            step.number(), step.toolName(), step.arguments(), step.result()));

            System.out.println();
            System.out.println(outcome.stoppedOnStepLimit()
                    ? "stopped: step limit reached without an answer"
                    : outcome.answer());
        };
    }
}
