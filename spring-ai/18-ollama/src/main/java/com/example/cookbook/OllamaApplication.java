package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Everything in this recipe runs on your machine. No key, no network, no per-token cost.
 * What you trade for that is speed and reliability, and this is where you find out how much.
 */
@SpringBootApplication
public class OllamaApplication {

    record Ticket(String title, String component, String severity, boolean customerFacing) {
    }

    public static void main(String[] args) {
        SpringApplication.run(OllamaApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("Answer in one short sentence.").build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient, EmbeddingModel embeddingModel) {
        return args -> {
            String question = args.length > 0 ? String.join(" ", args) : "What is a Java virtual thread?";

            System.out.println("> " + question);
            System.out.println(chatClient.prompt().user(question).call().content());
            System.out.println();

            System.out.println("> structured output from a local model");
            RetryingEntity.call(chatClient,
                            """
                            Turn this report into a ticket:
                            "Checkout returns 500 for EU customers since the 14:00 deploy."
                            """,
                            Ticket.class, 3)
                    .ifPresentOrElse(
                            ticket -> System.out.println(ticket),
                            () -> System.out.println("no usable JSON after 3 attempts"));
            System.out.println();

            float[] vector = embeddingModel.embed("local embeddings work the same way");
            System.out.println("embedding dimensions: " + vector.length);
        };
    }
}
