package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;

@SpringBootApplication
public class StructuredOutputApplication {

    public static void main(String[] args) {
        SpringApplication.run(StructuredOutputApplication.class, args);
    }

    /** Records map cleanly to JSON schema, so they make the best output targets. */
    public record Recipe(String title, List<String> ingredients, int prepMinutes, boolean vegetarian) {
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient) {
        return args -> {
            String dish = args.length > 0 ? String.join(" ", args) : "mushroom risotto";

            // entity() appends the JSON schema to the prompt and parses the reply back into the record.
            Recipe recipe = chatClient.prompt()
                    .user("Give me a recipe for " + dish + ".")
                    .call()
                    .entity(Recipe.class);

            System.out.println(recipe);

            // Collections need a type reference, because generics are erased at runtime.
            List<Recipe> variants = chatClient.prompt()
                    .user("Give me two quick variants of " + dish + ".")
                    .call()
                    .entity(new ParameterizedTypeReference<List<Recipe>>() {
                    });

            variants.forEach(System.out::println);
        };
    }
}
