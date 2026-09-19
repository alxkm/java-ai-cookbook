package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication
public class MultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiAgentApplication.class, args);
    }

    @Bean
    SupportDesk supportDesk(ChatClient.Builder builder) {
        return new SupportDesk(builder);
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(SupportDesk supportDesk) {
        return args -> {
            List<String> messages = args.length > 0
                    ? List.of(String.join(" ", args))
                    : List.of("Where is my order A-1002?",
                            "I was charged twice for the same invoice.",
                            "Do you ship to Norway?");

            for (String message : messages) {
                SupportDesk.Handled handled = supportDesk.handle(message);

                System.out.println("> " + message);
                System.out.printf("  routed to %s (%s)%n", handled.desk(), handled.reason());
                System.out.println(handled.answer());
                System.out.println();
            }
        };
    }
}
