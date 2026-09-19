package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class StreamingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamingApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You are a terse Java assistant.").build();
    }

    /** Prints one streamed answer at startup, then the SSE endpoint stays up. */
    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient) {
        return args -> {
            String question = "Explain Java virtual threads to a backend engineer.";
            System.out.println("> " + question);

            chatClient.prompt()
                    .user(question)
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        System.out.print(token);
                        System.out.flush();
                    })
                    .blockLast();

            System.out.println("\n\nSSE endpoint: curl -N 'http://localhost:8080/chat/stream?q=hello'");
        };
    }
}
