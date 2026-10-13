package com.example.cookbook;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public class Memory {

    /** Messages kept per conversation, not counting the system prompt, which rides on top. */
    static final int WINDOW = 10;

    interface Assistant {

        @SystemMessage("You are a terse assistant.")
        String chat(@MemoryId String conversationId, @UserMessage String message);
    }

    public static void main(String[] args) {
        // The window and the storage are two separate decisions, and the default wiring quietly
        // makes one choice for both: leave the store out and the history lives in a map that dies
        // with the process. maxMessages bounds what the model sees; the store decides what
        // survives a restart.
        try (JdbcChatMemoryStore store = new JdbcChatMemoryStore("jdbc:h2:file:./data/chat-memory")) {
            Assistant assistant = assistant(Models.chat(), store);

            ask(assistant, "alice", "My name is Alice and I work on payments.");
            ask(assistant, "bob", "My name is Bob and I work on search.");
            ask(assistant, "alice", "What do I work on?");
            ask(assistant, "bob", "What do I work on?");

            System.out.println("conversations on disk: " + store.conversationIds());
            System.out.println("run this again and Alice already knows what she works on.");
        }
    }

    /**
     * The provider is called lazily, the first time an id is seen. Each memory is a window over the
     * store, wrapped so that what is sent never starts with an answer whose question was evicted.
     */
    static Assistant assistant(ChatModel model, ChatMemoryStore store) {
        return AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> new TurnAlignedChatMemory(MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(WINDOW)
                        .chatMemoryStore(store)
                        .build()))
                .build();
    }

    private static void ask(Assistant assistant, String conversationId, String message) {
        System.out.printf("[%s] > %s%n[%s] %s%n%n",
                conversationId, message, conversationId, assistant.chat(conversationId, message));
    }
}
