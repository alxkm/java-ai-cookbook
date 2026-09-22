package com.example.cookbook;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class MemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoryApplication.class, args);
    }

    /**
     * The window and the storage are two separate decisions, which is easy to miss because the
     * default wiring makes one choice for both.
     *
     * MessageWindowChatMemory is the window: the last N messages go to the model, older turns are
     * dropped rather than summarised. The repository underneath it is the storage, and swapping it
     * is what decides whether the history survives a restart. The window does not change either way.
     */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem("You are a terse assistant.")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    // The demo output is not wanted during tests; @SpringBootTest runs CommandLineRunner beans.
    @Profile("!test")
    @Bean
    CommandLineRunner run(ChatClient chatClient, ChatMemoryRepository repository) {
        return args -> {
            // Two separate conversations, same ChatClient. The id is what keeps them apart.
            ask(chatClient, "alice", "My name is Alice and I work on payments.");
            ask(chatClient, "bob", "My name is Bob and I work on search.");
            ask(chatClient, "alice", "What do I work on?");
            ask(chatClient, "bob", "What do I work on?");

            // Proof that the history is not only in this JVM: it is rows in a table, and the ids
            // are there to be listed, resumed or deleted without going through the model.
            System.out.println("conversations on disk: " + repository.findConversationIds());
            System.out.println("run this again and Alice already knows what she works on.");
        };
    }

    private void ask(ChatClient chatClient, String conversationId, String message) {
        String answer = chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        System.out.printf("[%s] > %s%n[%s] %s%n%n", conversationId, message, conversationId, answer);
    }
}
