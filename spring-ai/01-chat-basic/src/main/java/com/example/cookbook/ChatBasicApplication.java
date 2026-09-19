package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class ChatBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatBasicApplication.class, args);
    }

    /**
     * ChatClient is the fluent entry point. The builder is auto-configured from the
     * starter on the classpath, so switching providers is a property change, not a code change.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are a terse Java assistant. Answer in at most three sentences.")
                .build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient) {
        return args -> {
            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Why does Java still not have value types in the language?";

            System.out.println("> " + question);
            System.out.println(chatClient.prompt().user(question).call().content());
        };
    }
}
