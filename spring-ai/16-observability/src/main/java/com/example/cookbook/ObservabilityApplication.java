package com.example.cookbook;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.List;

@SpringBootApplication
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, MeterRegistry registry) {
        return builder
                .defaultSystem("Answer in one sentence.")
                .defaultAdvisors(new TokenUsageAdvisor(registry))
                .build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient, MeterRegistry registry) {
        return args -> {
            List<String> questions = args.length > 0
                    ? List.of(String.join(" ", args))
                    : List.of("What is a virtual thread?",
                            "What is a record?",
                            "What is a sealed interface?");

            for (String question : questions) {
                System.out.println("> " + question);
                System.out.println(chatClient.prompt().user(question).call().content());
            }

            System.out.println();
            System.out.println("--- meters ---");
            registry.getMeters().stream()
                    .filter(meter -> meter.getId().getName().startsWith("cookbook.")
                            || meter.getId().getName().startsWith("gen_ai."))
                    .sorted(java.util.Comparator.comparing(meter -> meter.getId().getName()))
                    .forEach(meter -> System.out.printf("%-28s %-40s %s%n",
                            meter.getId().getName(), meter.getId().getTags(), meter.measure()));
        };
    }
}
