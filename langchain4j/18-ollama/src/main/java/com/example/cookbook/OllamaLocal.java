package com.example.cookbook;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Everything in this recipe runs on your machine. No key, no network, no per-token cost.
 * What you trade for that is speed and reliability, and this is where you find out how much.
 */
public class OllamaLocal {

    public record Ticket(String title, String component, String severity, boolean customerFacing) {
    }

    public static void main(String[] args) {
        String question = args.length > 0 ? String.join(" ", args) : "What is a Java virtual thread?";

        ChatModel model = LocalModels.chat();

        System.out.println("> " + question);
        System.out.println(model.chat("Answer in one short sentence. " + question));
        System.out.println();

        System.out.println("> structured output from a local model");
        RetryingEntity.call(model,
                        """
                        Turn this report into a ticket:
                        "Checkout returns 500 for EU customers since the 14:00 deploy."
                        """,
                        Ticket.class, 3)
                .ifPresentOrElse(
                        ticket -> System.out.println(ticket),
                        () -> System.out.println("no usable JSON after 3 attempts"));
        System.out.println();

        var vector = LocalModels.embedding().embed("local embeddings work the same way").content();
        System.out.println("embedding dimensions: " + vector.dimension());
    }
}
