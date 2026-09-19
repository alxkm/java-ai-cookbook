package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class ToolCallingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolCallingApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are a support agent. Use the tools instead of guessing.")
                .build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient) {
        return args -> {
            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Where is order A-1002 and when will it arrive?";

            System.out.println("> " + question);

            // Spring AI runs the whole loop: model asks for a tool, the tool runs,
            // the result goes back, repeat until the model answers in plain text.
            String answer = chatClient.prompt()
                    .user(question)
                    .tools(new OrderTools())
                    .call()
                    .content();

            System.out.println(answer);
        };
    }
}
