package com.example.cookbook;

public class AgentReact {

    private static final String SYSTEM_PROMPT = """
            You are a support agent. Work in small steps.
            Use a tool whenever a fact is not already in the conversation, and never guess an order status.
            When you have enough information, answer in one short paragraph.
            """;

    public static void main(String[] args) {
        String question = args.length > 0
                ? String.join(" ", args)
                : "Compare orders A-1001 and A-1002: which one arrives first, and how many days from today?";

        AgentLoop.Outcome outcome = new AgentLoop(Models.chat(), 6, new OrderTools())
                .run(SYSTEM_PROMPT, question);

        System.out.println("> " + question);
        System.out.println();

        outcome.steps().forEach(step ->
                System.out.printf("  step %d: %s%s -> %s%n",
                        step.number(), step.toolName(), step.arguments(), step.result()));

        System.out.println();
        System.out.println(outcome.stoppedOnStepLimit()
                ? "stopped: step limit reached without an answer"
                : outcome.answer());
    }
}
