package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

@SpringBootApplication
public class MultimodalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultimodalApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient, @Value("classpath:/images/chart.png") Resource chart) {
        return args -> {
            String question = args.length > 0
                    ? String.join(" ", args)
                    : "Describe this chart. How many bars are there, and which one is tallest?";

            System.out.println("> " + question);

            // Text and image go into the same user message. The model sees one message with two parts.
            String answer = chatClient.prompt()
                    .user(user -> user
                            .text(question)
                            .media(MimeTypeUtils.IMAGE_PNG, chart))
                    .call()
                    .content();

            System.out.println(answer);
        };
    }
}
