package com.example.cookbook;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public class Memory {

    interface Assistant {

        @SystemMessage("You are a terse assistant.")
        String chat(@MemoryId String conversationId, @UserMessage String message);
    }

    public static void main(String[] args) {
        // One memory per conversation id. The provider is called lazily, the first time an id is seen.
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(Models.chat())
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(10)
                        .build())
                .build();

        ask(assistant, "alice", "My name is Alice and I work on payments.");
        ask(assistant, "bob", "My name is Bob and I work on search.");
        ask(assistant, "alice", "What do I work on?");
        ask(assistant, "bob", "What do I work on?");
    }

    private static void ask(Assistant assistant, String conversationId, String message) {
        System.out.printf("[%s] > %s%n[%s] %s%n%n",
                conversationId, message, conversationId, assistant.chat(conversationId, message));
    }
}
